package ai.yuzu.perf;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.chat.ChatService;
import ai.yuzu.common.error.ErrorCode;
import ai.yuzu.llm.usage.TokenBudget;
import ai.yuzu.llm.usage.TokenMeter;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SerializedEvent;
import ai.yuzu.realtime.SseHub;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.room.UserView;
import ai.yuzu.settings.LlmProvider;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.support.FakeLlmServer;
import ai.yuzu.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v0.0.31 🍊 With a tiny budget, 8 agents stop calling the provider instead of burning through the account.
 *
 * <p>The budget is deliberately small enough that the first handful of calls exhausts it. After that the
 * priority gate refuses every acquisition, the pause is announced once on the realtime stream, and no
 * further HTTP request reaches the provider — however many messages the humans keep posting. Raising the
 * ceiling puts the agents back to work.</p>
 */
@IntegrationTest
@TestPropertySource(properties = {
        "yuzu.llm.budget.max-total-tokens=6000",
        "yuzu.llm.budget.max-cost-usd=1000",
        "yuzu.llm.budget.usd-per-million-tokens=2.50"})
class BudgetPauseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BudgetPauseIntegrationTest.class);
    private static final int AGENTS = 8;

    @Autowired
    private AgentService agents;
    @Autowired
    private HumanUserService users;
    @Autowired
    private ChatService chat;
    @Autowired
    private SettingsService settings;
    @Autowired
    private TokenBudget budget;
    @Autowired
    private TokenMeter meter;
    @Autowired
    private SseHub hub;
    @Autowired
    private JdbcClient jdbc;

    private FakeLlmServer server;
    private String roomId;
    private final List<AgentProfile> room = new ArrayList<>();
    private UserView alice;

    @BeforeEach
    void setUp() throws Exception {
        room.clear();
        server = new FakeLlmServer();
        server.defaultFor(FakeLlmServer.schema("chat"), FakeLlmServer.completion(
                "{\"reasoning\":\"mine\",\"decision\":\"FORWARD\",\"replyText\":null,\"ackText\":null,"
                        + "\"topicClosed\":false}", 1400, 0, 20));
        server.defaultFor(FakeLlmServer.schema("safety"), FakeLlmServer.completion(
                "{\"reasoning\":\"clean\",\"verdict\":\"SAFE\",\"violations\":[],\"masks\":[],"
                        + "\"userFacingReason\":null}", 1500, 0, 10));
        server.defaultFor(FakeLlmServer.schema("main"), FakeLlmServer.completion(
                "{\"thought\":\"noted\",\"mode\":\"END\",\"actions\":[],\"nextThought\":null}", 3000, 0, 30));
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-6060");
        roomId = String.format("room-%04x", ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        jdbc.sql("INSERT INTO room (id, name, created_at) VALUES (:id, 'Budget room', UTC_TIMESTAMP(3))")
                .param("id", roomId).update();
        String[] names = {"Yuzu", "Lime", "Kumquat", "Pomelo", "Citron", "Sudachi", "Calamansi", "Bergamot"};
        for (int i = 0; i < AGENTS; i++) {
            room.add(agents.createNamed(roomId, new CreateAgentRequest(Role.values()[i % Role.values().length],
                    null, null, null, null, null), names[i]));
        }
        alice = users.join(roomId, "Alice");
    }

    @AfterEach
    void tearDown() {
        room.forEach(p -> agents.retire(p.agentId()));
        budget.setLimits(0, null);
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.31 🍊 The budget trips, every further call is refused, and raising the ceiling resumes the room. */
    @Test
    void exhaustedBudgetStopsEveryAgentAndCanBeRaised() throws Exception {
        assertThat(budget.isPaused()).isFalse();
        for (int i = 0; i < 12; i++) {
            chat.postHuman(roomId, alice.id(), "Sprint update " + i + ": the Citrus Spark bottle ships Friday.");
        }
        await(budget::isPaused);
        int afterPause = awaitStableRequestCount();

        TokenBudget.Status status = budget.status();
        assertThat(status.paused()).isTrue();
        assertThat(status.usedTokens()).isGreaterThanOrEqualTo(6_000);
        assertThat(status.maxTotalTokens()).isEqualTo(6_000);
        assertThat(status.reason()).contains("token budget is used up");
        assertThat(status.pausedAt()).isNotBlank();
        log.info("🍊 budget: paused after {} model requests and {} billable tokens ({} USD)",
                afterPause, status.usedTokens(), status.usedUsd());

        // The platform-wide banner is published exactly once, however many agents ran into the ceiling.
        List<SerializedEvent> events = hub.bufferedEvents().stream()
                .filter(e -> e.type() == EventType.ERROR && e.json().contains(ErrorCode.BUDGET_EXHAUSTED.name()))
                .toList();
        assertThat(events.stream().filter(e -> e.json().contains("\"maxTotalTokens\"")).toList())
                .as("one banner for the console").hasSize(1);
        // Each module still reports its own degradation, so the trace shows why every agent went quiet.
        assertThat(events.stream().filter(e -> e.json().contains("\"context\"")).toList())
                .as("per-module degradations").isNotEmpty();

        // No further human message may reach the provider.
        for (int i = 0; i < 12; i++) {
            chat.postHuman(roomId, alice.id(), "Another update " + i + " while the budget is exhausted.");
        }
        Thread.sleep(600);
        assertThat(awaitStableRequestCount()).as("a paused budget must send nothing").isEqualTo(afterPause);
        assertThat(budget.resume()).as("resuming without headroom must fail").isFalse();

        // Raising the ceiling puts the agents back to work.
        budget.setLimits(10_000_000, null);
        assertThat(budget.isPaused()).isFalse();
        chat.postHuman(roomId, alice.id(), "We are funded again, please continue.");
        await(() -> server.requests().size() > afterPause);
        assertThat(meter.billableTokens()).isGreaterThan(status.usedTokens());
    }

    /** v0.0.31 🍊 Waits until the model request count stops changing and returns it. */
    private int awaitStableRequestCount() throws InterruptedException {
        int previous = -1;
        int stable = 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (stable < 20) {
            Thread.sleep(25);
            int now = server.requests().size();
            stable = now == previous ? stable + 1 : 0;
            previous = now;
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the model request count never settled");
            }
        }
        return previous;
    }

    /** v0.0.31 🍊 Polls a condition with a hard deadline. */
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not reached in time");
            }
            Thread.sleep(10);
        }
    }
}
