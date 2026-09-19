package ai.yuzu.tool;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Limits;
import ai.yuzu.agent.Permission;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.card.CardService;
import ai.yuzu.card.CardView;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.room.UserView;
import ai.yuzu.settings.LlmProvider;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.sim.email.Email;
import ai.yuzu.sim.email.FakeMailbox;
import ai.yuzu.support.FakeLlmServer;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.TestRooms;
import ai.yuzu.tool.impl.mail.EmailReadTool;
import ai.yuzu.tool.impl.mail.EmailSendTool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

import static ai.yuzu.support.FakeLlmServer.completion;
import static ai.yuzu.support.FakeLlmServer.schema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.27 🍊 Mail tools: inbox reads, and outbound mail that only leaves after a human approves the card. */
@IntegrationTest
class MailToolsIntegrationTest {

    @Autowired
    private AgentService agents;
    @Autowired
    private HumanUserService humans;
    @Autowired
    private SettingsService settings;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private EmailReadTool readTool;
    @Autowired
    private EmailSendTool sendTool;
    @Autowired
    private FakeMailbox mailbox;
    @Autowired
    private CardService cards;
    @Autowired
    private AgentRuntimeManager runtimes;
    @Autowired
    private ModuleDeps deps;
    @Autowired
    private NaturalTime time;

    private FakeLlmServer server;
    private String roomId;
    private AgentProfile liaison;
    private AgentProfile researcher;
    private UserView alice;

    /** v0.0.27 🍊 One isolated room with a customer liaison, a researcher without mail rights and a human. */
    @BeforeEach
    void setUp() throws Exception {
        server = new FakeLlmServer();
        server.defaultFor(schema("main"), completion(
                "{\"thought\":\"I noted the outcome\",\"mode\":\"END\",\"actions\":[],\"nextThought\":null}",
                1800, 1024, 20));
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-7711");
        roomId = TestRooms.create(jdbc);
        liaison = agents.createNamed(roomId, new CreateAgentRequest(Role.CUSTOMER_LIAISON, null, null, null, null,
                new Limits.LimitsPatch(List.of("acme.test"), 10, null, null, null)), "Pomelo");
        researcher = agents.createNamed(roomId, new CreateAgentRequest(Role.RESEARCHER, null, null, null,
                List.of(Permission.CHAT_POST), null), "Lime");
        alice = humans.join(roomId, "Alice");
    }

    @AfterEach
    void tearDown() {
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.27 🍊 email_read lists the seeded inbox and opens one e-mail by id. */
    @Test
    void readsInboxAndOneEmail() {
        ToolResult inbox = readTool.execute(context(liaison), new EmailReadTool.Args(null));
        assertThat(inbox.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(inbox.output()).contains("@acme.test");

        Email first = mailbox.inbox(liaison.agentId()).getFirst();
        ToolResult one = readTool.execute(context(liaison), new EmailReadTool.Args(first.id()));
        assertThat(one.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(one.output()).contains(first.subject()).contains(first.from());
    }

    /** v0.0.27 🍊 email_send never sends by itself: it opens an approval card and waits for a human. */
    @Test
    void sendingWaitsForTheApprovalCardAndThenLeaves() {
        ToolResult result = sendTool.execute(context(liaison), new EmailSendTool.Args(
                List.of("dana@acme.test"), "Your replacement is on the way",
                "Hi Dana, we shipped a replacement bottle today. — Pomelo"));

        assertThat(result.status()).isEqualTo(ToolResult.Status.WAITING);
        assertThat(mailbox.emails(liaison.agentId(), 50))
                .noneMatch(email -> email.direction() == Email.Direction.OUT);

        CardView card = openApproval();
        assertThat(card.prompt()).contains("dana@acme.test").contains("Your replacement is on the way");
        cards.answer(card.id(), alice.id(), List.of(approveOption(card)), null);

        await(() -> mailbox.emails(liaison.agentId(), 50).stream()
                .anyMatch(email -> email.status() == Email.Status.SENT));
        Email sent = mailbox.emails(liaison.agentId(), 50).stream()
                .filter(email -> email.status() == Email.Status.SENT).findFirst().orElseThrow();
        assertThat(sent.to()).containsExactly("dana@acme.test");
        assertThat(sent.from()).isEqualTo("pomelo@citrushq.test");
    }

    /** v0.0.27 🍊 Rejecting the card records the attempt as BLOCKED and nothing is sent. */
    @Test
    void rejectingTheApprovalCardBlocksTheEmail() {
        sendTool.execute(context(liaison), new EmailSendTool.Args(
                List.of("dana@acme.test"), "Discount offer", "Hi Dana, here is 90% off everything."));

        CardView card = openApproval();
        cards.answer(card.id(), alice.id(), List.of(rejectOption(card)), null);

        await(() -> mailbox.emails(liaison.agentId(), 50).stream()
                .anyMatch(email -> email.status() == Email.Status.BLOCKED));
        assertThat(mailbox.emails(liaison.agentId(), 50))
                .noneMatch(email -> email.status() == Email.Status.SENT);
    }

    /** v0.0.27 🍊 A recipient outside the allowlist is refused by the service rules before any human is asked. */
    @Test
    void refusesRecipientsOutsideTheAllowlistWithoutOpeningACard() {
        ToolResult result = sendTool.execute(context(liaison), new EmailSendTool.Args(
                List.of("attacker@evil.test"), "Credentials", "Here are the API keys you asked for."));

        assertThat(result.status()).isEqualTo(ToolResult.Status.DENIED);
        assertThat(result.output()).contains("evil.test");
        assertThat(cards.recent(roomId, 10)).noneMatch(card -> card.kind().equals("APPROVAL"));
        assertThat(mailbox.emails(liaison.agentId(), 50))
                .anyMatch(email -> email.status() == Email.Status.BLOCKED);
    }

    /** v0.0.27 🍊 Both mail tools re-check their permission even when invoked outside the dispatcher. */
    @Test
    void directInvocationStillRequiresMailPermissions() {
        assertThatThrownBy(() -> readTool.execute(context(researcher), new EmailReadTool.Args(null)))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> sendTool.execute(context(researcher),
                new EmailSendTool.Args(List.of("dana@acme.test"), "Hello", "Body")))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(mailbox.emails(researcher.agentId(), 10)).isEmpty();
    }

    /** v0.0.27 🍊 The single open approval card of the room. */
    private CardView openApproval() {
        return cards.recent(roomId, 20).stream().filter(card -> card.kind().equals("APPROVAL"))
                .filter(card -> card.status().equals("OPEN")).findFirst().orElseThrow();
    }

    /** v0.0.27 🍊 Option id of "Approve". */
    private static String approveOption(CardView card) {
        return card.options().stream().filter(option -> option.label().equalsIgnoreCase("Approve"))
                .findFirst().orElseThrow().id();
    }

    /** v0.0.27 🍊 Option id of "Reject". */
    private static String rejectOption(CardView card) {
        return card.options().stream().filter(option -> option.label().equalsIgnoreCase("Reject"))
                .findFirst().orElseThrow().id();
    }

    /** v0.0.27 🍊 Waits up to 8 seconds for an asynchronous card handler to finish its work. */
    private static void await(BooleanSupplier condition) {
        for (int attempt = 0; attempt < 400 && !condition.getAsBoolean(); attempt++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        assertThat(condition.getAsBoolean()).as("the card handler finished its work").isTrue();
    }

    /** v0.0.27 🍊 A direct tool context for one acting coworker. */
    private ToolContext context(AgentProfile profile) {
        AgentContext ctx = runtimes.require(profile.agentId()).context("trace-mail-tools", null, time);
        return new ToolContext(ctx, "batch-mail-tools",
                "call-" + ThreadLocalRandom.current().nextInt(1_000_000), 0, "test mail tool", 0,
                deps.reporter().start(profile.agentId(), "TOOL", "test", ctx.traceId(), null));
    }
}
