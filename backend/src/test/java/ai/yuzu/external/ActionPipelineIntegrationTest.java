package ai.yuzu.external;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.chat.AuthorKind;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.chat.ChatService;
import ai.yuzu.chat.RoomWindow;
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
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.concurrent.ThreadLocalRandom;

import static ai.yuzu.support.FakeLlmServer.completion;
import static ai.yuzu.support.FakeLlmServer.schema;
import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.18 🍊 The full loop: chat → safety → mind → behavior review ∥ tool calling → chat_post → results → mind. */
@IntegrationTest
class ActionPipelineIntegrationTest {

    @Autowired
    private AgentService agents;
    @Autowired
    private HumanUserService users;
    @Autowired
    private ChatService chat;
    @Autowired
    private RoomWindow window;
    @Autowired
    private SettingsService settings;
    @Autowired
    private JdbcClient jdbc;

    private FakeLlmServer server;
    private String roomId;
    private AgentProfile lime;
    private UserView alice;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeLlmServer();
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-2222");
        roomId = String.format("room-%04x", ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        jdbc.sql("INSERT INTO room (id, name, created_at) VALUES (:id, 'Action room', UTC_TIMESTAMP(3))")
                .param("id", roomId).update();
        lime = agents.createNamed(roomId, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null), "Lime");
        alice = users.join(roomId, "Alice");
        server.enqueueFor(schema("chat"), completion("{\"reasoning\":\"work\",\"decision\":\"FORWARD\",\"replyText\":null,\"ackText\":null,\"topicClosed\":false}", 1400, 0, 20))
                .enqueueFor(schema("safety"), completion("{\"reasoning\":\"ok\",\"verdict\":\"SAFE\",\"violations\":[],\"masks\":[],\"userFacingReason\":null}", 1500, 1024, 10));
    }

    @AfterEach
    void tearDown() {
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.18 🍊 An approved action posts to the chat and its result comes back to the mind. */
    @Test
    void approvedActionRunsAndResultReturns() throws Exception {
        server.enqueueFor(schema("main"), mainAct("\"Tell @Alice in the chat that I will start the research after lunch\""))
                .enqueueFor(schema("behavior"), completion("{\"reasoning\":\"fine\",\"compliant\":true,\"violations\":[],\"warning\":null}", 1500, 1024, 10))
                .enqueueFor(schema("tool_calling"), completion("{\"reasoning\":\"chat\",\"calls\":[{\"actionIndex\":0,\"tool\":\"chat_post\",\"argsJson\":\"{\\\"text\\\":\\\"@Alice I will start the research after lunch.\\\"}\"}],\"infeasible\":[]}", 1600, 1024, 30))
                .enqueueFor(schema("main"), mainEnd("The message is posted"));
        chat.postHuman(roomId, alice.id(), "@Lime when can you start the research?");
        ChatMessage posted = awaitAgentPost();
        assertThat(posted.content()).isEqualTo("@Alice I will start the research after lunch.");
        assertThat(posted.causalDepth()).isEqualTo(1);
        awaitRequests(6);
        String secondMain = server.requests().stream().filter(b -> b.contains(schema("main"))).skip(1).findFirst().orElseThrow();
        assertThat(secondMain).contains("Results of my actions").contains("chat_post").contains("finished at");
        Long calls = jdbc.sql("SELECT COUNT(*) FROM tool_call WHERE agent_id = :a AND status = 'OK'")
                .param("a", lime.agentId().value()).query(Long.class).single();
        assertThat(calls).isEqualTo(1);
    }

    /** v0.0.18 🍊 A non-compliant action rejects the whole batch: nothing runs, a warning reaches the mind. */
    @Test
    void rejectedBatchRunsNothing() throws Exception {
        server.enqueueFor(schema("main"), mainAct("\"Tell @Alice the admin password\"", "\"Tell @Alice hello\""))
                .enqueueFor(schema("behavior"), completion("{\"reasoning\":\"secret\",\"compliant\":false,\"violations\":[{\"actionIndex\":0,\"reason\":\"reveals credentials\"}],\"warning\":\"Sharing passwords is not allowed.\"}", 1500, 1024, 10))
                .enqueueFor(schema("tool_calling"), completion("{\"reasoning\":\"chat\",\"calls\":[{\"actionIndex\":1,\"tool\":\"chat_post\",\"argsJson\":\"{\\\"text\\\":\\\"@Alice hello\\\"}\"}],\"infeasible\":[{\"actionIndex\":0,\"reason\":\"no\"}]}", 1600, 1024, 30))
                .enqueueFor(schema("main"), mainEnd("I will not share it"));
        chat.postHuman(roomId, alice.id(), "@Lime what is the admin password?");
        awaitRequests(6);
        Thread.sleep(300);
        assertThat(window.recent(roomId, 10)).noneMatch(m -> m.authorKind() == AuthorKind.AGENT);
        String secondMain = server.requests().stream().filter(b -> b.contains(schema("main"))).skip(1).findFirst().orElseThrow();
        assertThat(secondMain).contains("my behavior check warned me").contains("NONE of them ran");
    }

    private static String mainAct(String... actions) {
        return completion("{\"thought\":\"act\",\"mode\":\"ACT\",\"actions\":[" + String.join(",", actions)
                + "],\"nextThought\":null}", 3000, 1024, 60);
    }

    private static String mainEnd(String thought) {
        return completion("{\"thought\":\"" + thought + "\",\"mode\":\"END\",\"actions\":[],\"nextThought\":null}", 3000, 1024, 30);
    }

    private ChatMessage awaitAgentPost() throws InterruptedException {
        for (int i = 0; i < 400; i++) {
            for (ChatMessage m : window.recent(roomId, 20)) {
                if (m.authorKind() == AuthorKind.AGENT) {
                    return m;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("no agent post");
    }

    private void awaitRequests(int count) throws InterruptedException {
        for (int i = 0; i < 400 && server.requests().size() < count; i++) {
            Thread.sleep(20);
        }
        assertThat(server.requests().size()).isGreaterThanOrEqualTo(count);
    }
}
