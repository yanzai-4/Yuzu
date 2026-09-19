package ai.yuzu.internal;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.chat.ChatService;
import ai.yuzu.internal.consciousness.ActionSubmitter;
import ai.yuzu.internal.memory.WorkingMemoryService;
import ai.yuzu.internal.memory.WorkingMemoryView;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.room.UserView;
import ai.yuzu.settings.LlmProvider;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.support.FakeLlmServer;
import ai.yuzu.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.28 🍊 Main consciousness end to end: THINK loops, ACT hand-off, working memory without duplicates, streak cap. */
@IntegrationTest
@Import(MainConsciousnessIntegrationTest.CapturingActions.class)
class MainConsciousnessIntegrationTest {

    /** v0.0.17 🍊 Captures submitted action batches instead of executing them. */
    @TestConfiguration
    static class CapturingActions {
        static final List<List<String>> SUBMITTED = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        ActionSubmitter capturingActionSubmitter() {
            return new ActionSubmitter() {
                @Override
                public void submit(AgentContext ctx, String runId, List<String> actions, int causalDepth) {
                    SUBMITTED.add(actions);
                }

                @Override
                public String inProgress(AgentContext ctx) {
                    return "(none)";
                }
            };
        }
    }

    @Autowired
    private AgentService agents;
    @Autowired
    private HumanUserService users;
    @Autowired
    private ChatService chat;
    @Autowired
    private SettingsService settings;
    @Autowired
    private WorkingMemoryService workingMemory;
    @Autowired
    private JdbcClient jdbc;

    private FakeLlmServer server;
    private String roomId;
    private AgentProfile lime;
    private UserView alice;

    @BeforeEach
    void setUp() throws Exception {
        CapturingActions.SUBMITTED.clear();
        server = new FakeLlmServer();
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-7777");
        roomId = String.format("room-%04x", ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        jdbc.sql("INSERT INTO room (id, name, created_at) VALUES (:id, 'Mind room', UTC_TIMESTAMP(3))")
                .param("id", roomId).update();
        lime = agents.createNamed(roomId, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null), "Lime");
        alice = users.join(roomId, "Alice");
    }

    @AfterEach
    void tearDown() {
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.17 🍊 THINK → THINK → ACT: two SELF loops, actions handed off, no duplicate working-memory entries. */
    @Test
    void thinkThinkAct() throws Exception {
        forwardAndGateSafe();
        server.enqueue(200, main("I need to plan this", "THINK", List.of(), "\"Which sources are best?\""))
                .enqueue(200, main("Industry reports first", "THINK", List.of(), "\"Then summarize\""))
                .enqueue(200, main("Ready", "ACT", List.of("\"Search the web for citrus juice market 2026\"",
                        "\"Tell @Alice I started the research\""), "null"));
        chat.postHuman(roomId, alice.id(), "@Lime please research the citrus juice market");
        awaitSubmitted(1);
        assertThat(CapturingActions.SUBMITTED.getFirst())
                .containsExactly("Search the web for citrus juice market 2026", "Tell @Alice I started the research");
        WorkingMemoryView memory = workingMemory.view(lime.agentId());
        assertThat(memory.entries()).hasSize(4);
        assertThat(memory.entries().stream().filter(e -> e.direction().equals("IN")).count()).isEqualTo(1);
        assertThat(memory.entries().get(1).text()).contains("I decided: THINK");
        assertThat(memory.entries().get(3).text()).contains("I decided: ACT").contains("1. Search the web");
        Long runs = jdbc.sql("SELECT COUNT(*) FROM main_run WHERE agent_id = :a").param("a", lime.agentId().value())
                .query(Long.class).single();
        assertThat(runs).isEqualTo(3);
    }

    /** v0.0.17 🍊 Endless thinking is cut off by code after 8 consecutive THINK steps. */
    @Test
    void thinkStreakIsCapped() throws Exception {
        forwardAndGateSafe();
        for (int i = 0; i < 12; i++) {
            server.enqueue(200, main("still thinking " + i, "THINK", List.of(), "\"more\""));
        }
        chat.postHuman(roomId, alice.id(), "@Lime ponder the meaning of citrus");
        for (int i = 0; i < 400 && deliberateRequests() < 10; i++) {
            Thread.sleep(20);
        }
        Thread.sleep(500);
        assertThat(deliberateRequests()).isEqualTo(2 + 8);
    }

    /**
     * v0.0.28 🍊 Requests of the deliberate path only.
     *
     * <p>Planning runs on every intake and the subconscious runs beside every non-subconscious pool message;
     * both answer from standing replies, so they never consume the scripted queue and never count here.</p>
     */
    private long deliberateRequests() {
        return server.requests().stream()
                .filter(b -> !b.contains(FakeLlmServer.schema("planning")))
                .filter(b -> !b.contains(FakeLlmServer.schema("subconscious")))
                .count();
    }

    private void forwardAndGateSafe() {
        server.defaultFor(FakeLlmServer.schema("subconscious"), FakeLlmServer.completion(
                "{\"reasoning\":\"nothing to add\",\"advice\":null,\"learn\":[],\"remember\":[],\"conflictUpdates\":[]}",
                1200, 0, 10));
        server.enqueue(200, FakeLlmServer.completion("{\"reasoning\":\"work\",\"decision\":\"FORWARD\",\"replyText\":null,\"ackText\":null,\"topicClosed\":false}", 1400, 0, 20))
                .enqueue(200, FakeLlmServer.completion("{\"reasoning\":\"ok\",\"verdict\":\"SAFE\",\"violations\":[],\"masks\":[],\"userFacingReason\":null}", 1500, 1024, 10));
    }

    private static String main(String thought, String mode, List<String> actions, String next) {
        return FakeLlmServer.completion("{\"thought\":\"" + thought + "\",\"mode\":\"" + mode + "\",\"actions\":["
                + String.join(",", actions) + "],\"nextThought\":" + next + "}", 3000, 1024, 60);
    }

    private static void awaitSubmitted(int count) throws InterruptedException {
        for (int i = 0; i < 400 && CapturingActions.SUBMITTED.size() < count; i++) {
            Thread.sleep(20);
        }
        assertThat(CapturingActions.SUBMITTED).hasSize(count);
    }
}
