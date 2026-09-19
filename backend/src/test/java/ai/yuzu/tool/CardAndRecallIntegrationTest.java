package ai.yuzu.tool;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.card.CardService;
import ai.yuzu.card.CardView;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.chat.MessageKind;
import ai.yuzu.chat.RoomWindow;
import ai.yuzu.common.error.ConflictException;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.room.UserView;
import ai.yuzu.settings.LlmProvider;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.support.FakeLlmServer;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.tool.impl.memory.MemoryReadTool;
import ai.yuzu.tool.impl.question.AskUserTool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static ai.yuzu.support.FakeLlmServer.completion;
import static ai.yuzu.support.FakeLlmServer.schema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.19 🍊 Question cards round-trip without any chat module; memory_read recalls by time and by topic. */
@IntegrationTest
class CardAndRecallIntegrationTest {

    @Autowired
    private AgentService agents;
    @Autowired
    private HumanUserService users;
    @Autowired
    private RoomWindow window;
    @Autowired
    private SettingsService settings;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private CardService cards;
    @Autowired
    private AskUserTool askUser;
    @Autowired
    private MemoryReadTool memoryRead;
    @Autowired
    private AgentRuntimeManager runtimes;
    @Autowired
    private ModuleDeps deps;
    @Autowired
    private NaturalTime time;

    private FakeLlmServer server;
    private String roomId;
    private AgentProfile lime;
    private UserView alice;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeLlmServer();
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-3333");
        roomId = newRoom("Card room");
        lime = agents.createNamed(roomId, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null), "Lime");
        alice = users.join(roomId, "Alice");
    }

    @AfterEach
    void tearDown() {
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.19 🍊 ask_user returns WAITING at once; the answer reaches the mind as new input; first answer wins. */
    @Test
    void questionCardRoundTrip() throws Exception {
        server.enqueueFor(schema("main"), completion("{\"thought\":\"Alice picked a tagline\",\"mode\":\"END\",\"actions\":[],\"nextThought\":null}", 3000, 1024, 30));
        ToolResult result = askUser.execute(toolContext(), new AskUserTool.Args("@Alice which tagline do you prefer?",
                List.of("Zest up your day", "Peel good energy"), true));
        assertThat(result.status()).isEqualTo(ToolResult.Status.WAITING);
        ChatMessage card = window.recent(roomId, 10).stream().filter(m -> m.kind() == MessageKind.QUESTION_CARD)
                .findFirst().orElseThrow();
        assertThat(card.fanout()).isFalse();
        CardView view = cards.recent(roomId, 5).getFirst();
        assertThat(view.status()).isEqualTo("OPEN");
        assertThat(view.options()).hasSize(2);

        UserView outsider = users.join(newRoom("Other"), "Mallory");
        assertThatThrownBy(() -> cards.answer(view.id(), outsider.id(), List.of("o1"), null))
                .isInstanceOf(PermissionDeniedException.class);

        CardView answered = cards.answer(view.id(), alice.id(), List.of("o2"), null);
        assertThat(answered.status()).isEqualTo("ANSWERED");
        assertThat(answered.answeredByName()).isEqualTo("Alice");
        assertThatThrownBy(() -> cards.answer(view.id(), alice.id(), List.of("o1"), null))
                .isInstanceOf(ConflictException.class);

        awaitRequests(1);
        String main = server.requests().stream().filter(b -> b.contains(schema("main"))).findFirst().orElseThrow();
        assertThat(main).contains("Peel good energy").contains("answered my question");
        assertThat(server.requests()).noneMatch(b -> b.contains(schema("chat")));
    }

    /** v0.0.19 🍊 A time phrase code understands needs no AI: only records from that window come back. */
    @Test
    void recallByTimePhrase() {
        insertWm("wm-old", "Alice asked about the budget", 40);
        insertWm("wm-new", "Alice asked me to compare citrus drink prices", 5);
        insertChat("I think the landing page needs a hero image", 5);
        ToolResult result = memoryRead.execute(toolContext(),
                new MemoryReadTool.Args("what did Alice ask 5 minutes ago", "5 minutes ago", List.of()));
        assertThat(result.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(result.output()).contains("compare citrus drink prices").contains("hero image")
                .doesNotContain("budget").contains("5 minutes ago");
        assertThat(server.requests()).isEmpty();
    }

    /** v0.0.19 🍊 Keywords find deep memories by FULLTEXT; an unknown time phrase goes to the memory-read module. */
    @Test
    void recallByTopicAndAiFallback() throws Exception {
        jdbc.sql("""
                        INSERT INTO deep_memory (agent_id, id, title, content, keywords, source, content_hash, created_at, updated_at)
                        VALUES (:a, :id, 'Citrus Spark launch', 'The Citrus Spark bottle launches on Friday with three beta customers.',
                            'launch spark', 'MANUAL', UNHEX(SHA2(:id, 256)), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                        """).param("a", lime.agentId().value()).param("id", "mem-" + lime.agentId().hex() + "-0000000001")
                .update();
        ToolResult topic = memoryRead.execute(toolContext(),
                new MemoryReadTool.Args("the Citrus Spark launch plan", null, List.of("Citrus Spark", "launch")));
        assertThat(topic.output()).contains("launches on Friday");
        assertThat(server.requests()).isEmpty();

        insertWm("wm-fallback", "I drafted the launch email for the beta customers", 90);
        String from = time.machine(time.nowInstant().minusSeconds(120 * 60));
        String to = time.machine(time.nowInstant().minusSeconds(60 * 60));
        server.enqueueFor(schema("memory_read"), completion("{\"reasoning\":\"launch morning\",\"keywords\":[],\"fromTime\":\""
                + from + "\",\"toTime\":\"" + to + "\"}", 1400, 1024, 20));
        ToolResult fallback = memoryRead.execute(toolContext(),
                new MemoryReadTool.Args("what did I do on the morning of the launch", "the morning of the launch", List.of()));
        awaitRequests(1);
        assertThat(server.requests().getFirst()).contains("Current time");
        assertThat(fallback.output()).contains("drafted the launch email");
    }

    private ToolContext toolContext() {
        AgentContext ctx = runtimes.require(lime.agentId()).context("trace-test", null, time);
        return new ToolContext(ctx, "batch-test", "call-" + ThreadLocalRandom.current().nextInt(1_000_000), 0,
                "test action", 0, deps.reporter().start(lime.agentId(), "TOOL", "test", "trace-test", null));
    }

    private void insertWm(String ref, String text, int minutesAgo) {
        jdbc.sql("""
                        INSERT INTO working_memory_entry (agent_id, id, run_id, direction, origin, origin_ref, text, tokens, compacted, created_at)
                        VALUES (:a, :id, NULL, 'IN', 'EXTERNAL', :ref, :text, 10, 1, DATE_SUB(UTC_TIMESTAMP(3), INTERVAL :m MINUTE))
                        """).param("a", lime.agentId().value())
                .param("id", "wm-" + lime.agentId().hex() + "-" + String.format("%010x", ThreadLocalRandom.current().nextLong(1L << 39)))
                .param("ref", ref).param("text", text).param("m", minutesAgo).update();
    }

    private void insertChat(String text, int minutesAgo) {
        jdbc.sql("""
                        INSERT INTO chat_message (room_id, id, author_kind, author_id, author_name, kind, content, mentions, created_at)
                        VALUES (:r, :id, 'HUMAN', :u, 'Alice', 'TEXT', :text, JSON_ARRAY(), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL :m MINUTE))
                        """).param("r", roomId)
                .param("id", "msg-0000-" + String.format("%010x", ThreadLocalRandom.current().nextLong(1L << 39)))
                .param("u", alice.id()).param("text", text).param("m", minutesAgo).update();
    }

    private String newRoom(String name) {
        String id = String.format("room-%04x", ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        jdbc.sql("INSERT INTO room (id, name, created_at) VALUES (:id, :name, UTC_TIMESTAMP(3))")
                .param("id", id).param("name", name).update();
        return id;
    }

    private void awaitRequests(int count) throws InterruptedException {
        for (int i = 0; i < 400 && server.requests().size() < count; i++) {
            Thread.sleep(20);
        }
        assertThat(server.requests().size()).isGreaterThanOrEqualTo(count);
    }
}
