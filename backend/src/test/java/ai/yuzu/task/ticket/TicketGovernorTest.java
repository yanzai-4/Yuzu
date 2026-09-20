package ai.yuzu.task.ticket;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.chat.ChatPost;
import ai.yuzu.chat.ChatService;
import ai.yuzu.chat.MessageKind;
import ai.yuzu.chat.RoomWindow;
import ai.yuzu.internal.consciousness.MainRunHandler;
import ai.yuzu.internal.consciousness.PoolMessage;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.room.UserView;
import ai.yuzu.settings.LlmProvider;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.support.FakeLlmServer;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.TestRooms;
import ai.yuzu.task.Actor;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v0.0.29 🍊 The ticket governor: code spots an engineer taking on a human request without a ticket.
 *
 * <p>The project manager @mentions the engineer exactly once; a closure reply ends the exchange for good.</p>
 */
@IntegrationTest
@Import(TicketGovernorTest.CapturingMain.class)
class TicketGovernorTest {

    /** v0.0.29 🍊 Captures pool messages so governance notices never need the main-consciousness model. */
    @TestConfiguration
    static class CapturingMain {
        static final List<PoolMessage> RECEIVED = new CopyOnWriteArrayList<>();

        /** v0.0.29 🍊 Replaces the real main-run handler. */
        @Bean
        @Primary
        MainRunHandler capturingMainRunHandler() {
            return (agent, batch) -> RECEIVED.addAll(batch);
        }
    }

    private static final String IGNORE = "{\"reasoning\":\"nothing for me\",\"decision\":\"IGNORE\","
            + "\"replyText\":null,\"ackText\":null,\"topicClosed\":false}";

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
    private TicketService tickets;
    @Autowired
    private JdbcClient jdbc;

    private FakeLlmServer server;
    private String room;
    private AgentProfile pm;
    private AgentProfile engineer;
    private UserView alice;

    /** v0.0.29 🍊 A room with a project manager, an engineer and Alice; every chat module stays silent. */
    @BeforeEach
    void setUp() throws Exception {
        CapturingMain.RECEIVED.clear();
        server = new FakeLlmServer();
        server.defaultFor(FakeLlmServer.schema("chat"), FakeLlmServer.completion(IGNORE, 1200, 0, 20));
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-3232");
        room = TestRooms.create(jdbc);
        pm = agents.createNamed(room, new CreateAgentRequest(Role.PROJECT_MANAGER, null, null, null, null, null),
                "Yuzu");
        engineer = agents.createNamed(room, new CreateAgentRequest(Role.ENGINEER, null, null, null, null, null),
                "Kumquat");
        alice = users.join(room, "Alice");
    }

    @AfterEach
    void tearDown() {
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.34 🍊 A human who @mentions a coworker gives it the work directly: the governor stays quiet. */
    @Test
    void staysQuietWhenTheHumanAskedThatCoworkerDirectly() throws Exception {
        chat.postHuman(room, alice.id(), "@Kumquat can you build the Citrus Spark landing page by Friday?");
        chat.post(ChatPost.agent(room, engineer.agentId().value(), engineer.name(),
                "@Alice on it, I'll build the landing page today.", 1, false, null));
        Thread.sleep(600);
        assertThat(window.recent(room, 20)).noneMatch(m -> m.kind() == MessageKind.TEXT
                && m.authorId().equals(pm.agentId().value()));
        assertThat(CapturingMain.RECEIVED).noneMatch(m -> m.text().contains("without a ticket"));
    }

    /** v0.0.29 🍊 One @mention from the PM, then silence: the closure reply stops the governor for good. */
    @Test
    void warnsOnceThenStaysQuietAfterAClosureReply() throws Exception {
        chat.postHuman(room, alice.id(), "Can someone build the Citrus Spark landing page by Friday?");
        ChatMessage commitment = chat.post(ChatPost.agent(room, engineer.agentId().value(), engineer.name(),
                "@Alice on it, I'll build the landing page today.", 1, false, null));

        ChatMessage warning = awaitWarning();
        assertThat(warning.authorId()).isEqualTo(pm.agentId().value());
        assertThat(warning.content()).contains("@Kumquat").contains("ticket");
        assertThat(warning.mentions()).containsExactly(engineer.agentId().value());
        assertThat(warning.closure()).isFalse();
        assertThat(warning.causalDepth()).isEqualTo(commitment.causalDepth() + 1);

        chat.post(ChatPost.agent(room, engineer.agentId().value(), engineer.name(),
                "@Yuzu understood, I stopped and I am waiting for your assignment.",
                warning.causalDepth() + 1, true, null));
        chat.post(ChatPost.agent(room, engineer.agentId().value(), engineer.name(),
                "@Alice sure, I'll also write the launch copy for you.", 1, false, null));
        Thread.sleep(700);

        assertThat(warnings()).hasSize(1);
    }

    /** v0.0.29 🍊 The PM's mind is told about the governance hint so it can create the missing ticket. */
    @Test
    void tellsTheProjectManagerToCreateATicket() throws Exception {
        chat.postHuman(room, alice.id(), "We need a landing page for the Citrus Spark bottle by Friday.");
        chat.post(ChatPost.agent(room, engineer.agentId().value(), engineer.name(),
                "@Alice I'll start building it right now.", 1, false, null));
        awaitWarning();

        for (int i = 0; i < 60 && CapturingMain.RECEIVED.stream().noneMatch(this::isGovernanceNotice); i++) {
            Thread.sleep(100);
        }
        assertThat(CapturingMain.RECEIVED).anyMatch(this::isGovernanceNotice);
    }

    /** v0.0.29 🍊 Work that already has a ticket is normal work: the governor says nothing. */
    @Test
    void staysSilentWhenTheWorkAlreadyHasATicket() throws Exception {
        tickets.create(room, Actor.of(alice), NewTicket.of("Citrus Spark landing page",
                "Build the landing page for Friday.").assignedTo(engineer.agentId()));

        chat.postHuman(room, alice.id(), "Can someone build the Citrus Spark landing page by Friday?");
        chat.post(ChatPost.agent(room, engineer.agentId().value(), engineer.name(),
                "@Alice on it, I'll build the landing page today.", 1, false, null));
        Thread.sleep(700);

        assertThat(warnings()).isEmpty();
    }

    /** v0.0.29 🍊 True for a pool message carrying the governor's hint to the PM. */
    private boolean isGovernanceNotice(PoolMessage message) {
        return message.agentId().equals(pm.agentId()) && message.text().contains("Kumquat")
                && message.text().contains("ticket");
    }

    /** v0.0.29 🍊 Waits for the PM's warning (at most five seconds). */
    private ChatMessage awaitWarning() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            List<ChatMessage> found = warnings();
            if (!found.isEmpty()) {
                return found.getFirst();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("The ticket governor did not make the project manager speak up.");
    }

    /** v0.0.29 🍊 Messages the PM posted that @mention the engineer. */
    private List<ChatMessage> warnings() {
        return window.recent(room, 200).stream()
                .filter(m -> m.authorId().equals(pm.agentId().value()) && m.kind() == MessageKind.TEXT
                        && m.mentions().contains(engineer.agentId().value()))
                .toList();
    }
}
