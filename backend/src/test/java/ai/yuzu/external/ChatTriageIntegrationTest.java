package ai.yuzu.external;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.chat.AuthorKind;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.chat.ChatPost;
import ai.yuzu.chat.ChatService;
import ai.yuzu.chat.RoomWindow;
import ai.yuzu.external.chat.LoopGuard;
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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.15 🍊 Chat triage end to end: direct replies with enforced @mentions, ignoring small talk, loop guard. */
@IntegrationTest
class ChatTriageIntegrationTest {

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
    private LoopGuard loopGuard;
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
        settings.saveApiKey("sk-fake-provider-key-9999");
        roomId = String.format("room-%04x", ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        jdbc.sql("INSERT INTO room (id, name, created_at) VALUES (:id, 'Triage room', UTC_TIMESTAMP(3))")
                .param("id", roomId).update();
        lime = agents.createNamed(roomId, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null), "Lime");
        alice = users.join(roomId, "Alice");
    }

    @AfterEach
    void tearDown() {
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.15 🍊 A simple question is answered directly and the reply @mentions the asker. */
    @Test
    void simpleQuestionGetsDirectReply() throws Exception {
        server.enqueue(200, FakeLlmServer.completion(
                "{\"reasoning\":\"Alice asks me a trivial arithmetic question\",\"decision\":\"REPLY\",\"replyText\":\"51\",\"ackText\":null,\"topicClosed\":true}",
                1400, 0, 30));
        chat.postHuman(roomId, alice.id(), "@Lime what's 17*3?");
        ChatMessage reply = awaitAgentMessage();
        assertThat(reply.content()).isEqualTo("@Alice 51");
        assertThat(reply.closure()).isTrue();
        assertThat(reply.causalDepth()).isEqualTo(1);
        assertThat(reply.mentions()).containsExactly(alice.id());
    }

    /** v0.0.15 🍊 Small talk is ignored: nothing is posted. */
    @Test
    void smallTalkIsIgnored() throws Exception {
        server.enqueue(200, FakeLlmServer.completion(
                "{\"reasoning\":\"small talk, not for me\",\"decision\":\"IGNORE\",\"replyText\":null,\"ackText\":null,\"topicClosed\":false}",
                1400, 0, 20));
        chat.postHuman(roomId, alice.id(), "nice weather today");
        for (int i = 0; i < 100 && server.requests().isEmpty(); i++) {
            Thread.sleep(20);
        }
        Thread.sleep(300);
        assertThat(server.requests()).hasSize(1);
        assertThat(window.recent(roomId, 10)).noneMatch(m -> m.authorKind() == AuthorKind.AGENT);
    }

    /** v0.0.15 🍊 More than 6 mentions between two agents within two minutes pauses the pair. */
    @Test
    void pairLimiterStopsPingPong() {
        AgentProfile yuzu = agents.createNamed(roomId, new CreateAgentRequest(Role.PROJECT_MANAGER, null, null, null,
                null, null), "Yuzu");
        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            ChatMessage fromYuzu = chat.post(new ChatPost(roomId, AuthorKind.AGENT, yuzu.agentId().value(), "Yuzu",
                    ai.yuzu.chat.MessageKind.TEXT, "@Lime ping " + i, 1, false, null, null, false,
                    ai.yuzu.chat.StreamState.NONE, null, List.of(lime.agentId().value())));
            if (loopGuard.allowReply(fromYuzu, lime.agentId().value())) {
                allowed++;
            }
        }
        assertThat(allowed).isEqualTo(7);
    }

    private ChatMessage awaitAgentMessage() throws InterruptedException {
        for (int i = 0; i < 250; i++) {
            List<ChatMessage> recent = window.recent(roomId, 20);
            for (ChatMessage m : recent) {
                if (m.authorKind() == AuthorKind.AGENT) {
                    return m;
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("no agent message appeared");
    }
}
