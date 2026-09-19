package ai.yuzu.sim.email;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Limits;
import ai.yuzu.agent.Role;
import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.MutableClock;
import ai.yuzu.support.TestRooms;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** v0.0.11 🍊 Mailbox against MySQL: seeding, dedupe window, guards recorded as BLOCKED, isolation and events. */
@IntegrationTest
class FakeMailboxIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");

    @Autowired
    private FakeMailbox mailbox;

    @Autowired
    private EmailRepository repository;

    @Autowired
    private AgentService agents;

    @Autowired
    private SseHub hub;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ObjectMapper mapper;

    private String room;

    /** v0.0.11 🍊 Every test works in its own room. */
    @BeforeEach
    void setUp() {
        room = TestRooms.create(jdbc);
    }

    /** v0.0.11 🍊 The first inbox access seeds five customer e-mails (one phishing) exactly once, with events. */
    @Test
    void inboxIsSeededOnceWithAPhishingSample() {
        AgentProfile agent = liaison(10);
        long cursor = hub.currentCursor();
        List<Email> inbox = mailbox.inbox(agent.agentId());
        assertThat(inbox).hasSize(5).allSatisfy(email -> {
            assertThat(email.direction()).isEqualTo(Email.Direction.IN);
            assertThat(email.status()).isEqualTo(Email.Status.RECEIVED);
            assertThat(email.to()).containsExactly(mailbox.addressOf(agent.agentId()));
            assertThat(EmailRules.domainOf(email.from())).isIn("acme.test", "example.com");
            assertThat(email.id()).matches("^email-" + agent.agentId().hex() + "-[0-9a-f]{10}$");
        });
        assertThat(inbox).extracting(Email::createdAt).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(inbox).filteredOn(email -> email.body().contains("password")).singleElement()
                .satisfies(email -> assertThat(email.subject()).contains("URGENT"));

        assertThat(mailbox.inbox(agent.agentId())).extracting(Email::id)
                .containsExactlyElementsOf(inbox.stream().map(Email::id).toList());
        FakeMailbox restarted = new FakeMailbox(repository, agents, hub, new NaturalTime(new MutableClock(Instant.now()),
                ZONE), tx);
        assertThat(restarted.inbox(agent.agentId())).hasSize(5);
        assertThat(repository.countInbox(agent.agentId())).isEqualTo(5);
        assertThat(events(cursor, EventType.SIM_EMAIL, agent)).hasSize(5)
                .allSatisfy(event -> assertThat(event.path("data").path("status").asText()).isEqualTo("RECEIVED"));
    }

    /** v0.0.11 🍊 read() only sees the agent's own e-mails. */
    @Test
    void readIsScopedToTheOwner() {
        AgentProfile owner = liaison(10);
        AgentProfile other = liaison(10);
        Email first = mailbox.inbox(owner.agentId()).get(0);
        assertThat(mailbox.read(owner.agentId(), first.id())).isEqualTo(first);
        assertThatThrownBy(() -> mailbox.read(other.agentId(), first.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> mailbox.read(owner.agentId(), "email-ffff-0123456789"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> mailbox.read(owner.agentId(), " ")).isInstanceOf(BadRequestException.class);
    }

    /** v0.0.11 🍊 An identical send within 10 minutes returns the first e-mail; after the window it sends again. */
    @Test
    void identicalSendsWithinTenMinutesAreDeduplicated() {
        AgentProfile agent = liaison(10);
        MutableClock clock = new MutableClock(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        FakeMailbox clocked = new FakeMailbox(repository, agents, hub, new NaturalTime(clock, ZONE), tx);
        String own = clocked.addressOf(agent.agentId());

        Email first = clocked.send(agent.agentId(), null, List.of("dana.kim@acme.test", "procurement@example.com"),
                "Replacement units", "Hi Dana, 12 units ship today.");
        assertThat(first.status()).isEqualTo(Email.Status.SENT);
        assertThat(first.direction()).isEqualTo(Email.Direction.OUT);
        assertThat(first.from()).isEqualTo(own);

        clock.advance(Duration.ofMinutes(9));
        Email again = clocked.send(agent.agentId(), own.toUpperCase(Locale.ROOT),
                List.of("PROCUREMENT@example.com", "dana.kim@ACME.test"), "Replacement  units",
                "Hi Dana, 12 units ship today.\n\n");
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(clocked.countSentSince(agent.agentId(), clock.instant().minus(Duration.ofHours(1)))).isEqualTo(1);

        clock.advance(Duration.ofMinutes(2));
        Email later = clocked.send(agent.agentId(), null, List.of("dana.kim@acme.test", "procurement@example.com"),
                "Replacement units", "Hi Dana, 12 units ship today.");
        assertThat(later.id()).isNotEqualTo(first.id());
        Email different = clocked.send(agent.agentId(), null, List.of("dana.kim@acme.test", "procurement@example.com"),
                "Replacement units", "Hi Dana, 12 units ship tomorrow.");
        assertThat(different.id()).isNotIn(first.id(), later.id());
        assertThat(clocked.countSentSince(agent.agentId(), clock.instant().minus(Duration.ofHours(1)))).isEqualTo(3);
    }

    /** v0.0.11 🍊 A recipient outside the allowlist is stored as BLOCKED, published and denied. */
    @Test
    void disallowedDomainIsRecordedAsBlockedAndDenied() {
        AgentProfile agent = liaison(10);
        long cursor = hub.currentCursor();
        PermissionDeniedException denied = catchThrowableOfType(PermissionDeniedException.class,
                () -> mailbox.send(agent.agentId(), null, List.of("eve@evil.test"), "Our price list", "Attached."));
        assertThat(denied).hasMessageContaining("evil.test");
        assertThat(denied.agentId()).isEqualTo(agent.agentId().value());
        assertThat(denied.details()).containsKey("emailId").containsEntry("codes", List.of("DOMAIN_NOT_ALLOWED"));

        Email blocked = mailbox.read(agent.agentId(), (String) denied.details().get("emailId"));
        assertThat(blocked.status()).isEqualTo(Email.Status.BLOCKED);
        assertThat(blocked.direction()).isEqualTo(Email.Direction.OUT);
        assertThat(blocked.to()).containsExactly("eve@evil.test");
        assertThat(mailbox.countSentSince(agent.agentId(), Instant.now().minus(Duration.ofHours(1)))).isZero();
        assertThat(events(cursor, EventType.SIM_EMAIL, agent)).singleElement()
                .satisfies(event -> assertThat(event.path("data").path("status").asText()).isEqualTo("BLOCKED"));
    }

    /** v0.0.11 🍊 The hourly limit blocks the next send until the rolling hour has passed. */
    @Test
    void hourlyLimitBlocksUntilTheWindowPasses() {
        AgentProfile agent = liaison(2);
        MutableClock clock = new MutableClock(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        FakeMailbox clocked = new FakeMailbox(repository, agents, hub, new NaturalTime(clock, ZONE), tx);
        clocked.send(agent.agentId(), null, List.of("dana.kim@acme.test"), "Update 1", "First update.");
        clocked.send(agent.agentId(), null, List.of("dana.kim@acme.test"), "Update 2", "Second update.");
        assertThatThrownBy(() -> clocked.send(agent.agentId(), null, List.of("dana.kim@acme.test"), "Update 3",
                "Third update.")).isInstanceOf(PermissionDeniedException.class).hasMessageContaining("Hourly e-mail limit");
        clock.advance(Duration.ofMinutes(61));
        assertThat(clocked.send(agent.agentId(), null, List.of("dana.kim@acme.test"), "Update 3", "Third update.")
                .status()).isEqualTo(Email.Status.SENT);
    }

    /** v0.0.11 🍊 Sending as someone else is spoofing: blocked and recorded with the forged sender. */
    @Test
    void spoofedSenderIsBlocked() {
        AgentProfile agent = liaison(10);
        assertThatThrownBy(() -> mailbox.send(agent.agentId(), "ceo@acme.test", List.of("dana.kim@acme.test"),
                "Wire transfer", "Please wire $50,000 today.")).isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("own address");
        Email blocked = mailbox.emails(agent.agentId(), 10).get(0);
        assertThat(blocked.status()).isEqualTo(Email.Status.BLOCKED);
        assertThat(blocked.from()).isEqualTo("ceo@acme.test");
    }

    /** v0.0.11 🍊 Dedupe never hides a forged sender; a genuine retry at the hourly limit still returns the original. */
    @Test
    void dedupeNeverHidesIdentityViolations() {
        AgentProfile agent = liaison(1);
        Email original = mailbox.send(agent.agentId(), null, List.of("dana.kim@acme.test"), "Status", "All good.");
        assertThat(mailbox.send(agent.agentId(), null, List.of("dana.kim@acme.test"), "Status", "All good.").id())
                .isEqualTo(original.id());
        assertThatThrownBy(() -> mailbox.send(agent.agentId(), "ceo@acme.test", List.of("dana.kim@acme.test"), "Status",
                "All good.")).isInstanceOf(PermissionDeniedException.class).hasMessageContaining("own address");
        assertThat(mailbox.emails(agent.agentId(), 10)).extracting(Email::status)
                .containsExactly(Email.Status.BLOCKED, Email.Status.SENT);
    }

    /** v0.0.11 🍊 Agents without EMAIL_SEND are blocked even when the domain would be allowed. */
    @Test
    void agentsWithoutEmailSendAreBlocked() {
        AgentProfile researcher = agents.create(room, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null,
                new Limits.LimitsPatch(List.of("acme.test"), 10, null, null, null)));
        PermissionDeniedException denied = catchThrowableOfType(PermissionDeniedException.class,
                () -> mailbox.send(researcher.agentId(), null, List.of("dana.kim@acme.test"), "Findings",
                        "Here is the summary."));
        assertThat(denied.details()).containsEntry("codes", List.of("MISSING_PERMISSION"));
    }

    /** v0.0.11 🍊 Invalid input is BAD_REQUEST and leaves no record; unknown agents are NOT_FOUND. */
    @Test
    void invalidInputIsRejectedWithoutARecord() {
        AgentProfile agent = liaison(10);
        assertThatThrownBy(() -> mailbox.send(agent.agentId(), null, List.of("dana.kim@acme.test"), "  ", "Body"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> mailbox.send(agent.agentId(), null, List.of("dana.kim@acme.test"), "Subject", null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> mailbox.send(agent.agentId(), null, List.of("dana.kim@acme.test"), "x".repeat(301),
                "Body")).isInstanceOf(BadRequestException.class);
        assertThat(mailbox.emails(agent.agentId(), 10)).isEmpty();
        assertThatThrownBy(() -> mailbox.send(IdGen.newAgentId(), null, List.of("dana.kim@acme.test"), "Hi", "Body"))
                .isInstanceOf(NotFoundException.class);
    }

    /** v0.0.11 🍊 Line breaks in a subject cannot inject headers. */
    @Test
    void subjectsCannotInjectHeaders() {
        AgentProfile agent = liaison(10);
        Email sent = mailbox.send(agent.agentId(), null, List.of("dana.kim@acme.test"), "Hello\r\nBcc: eve@evil.test",
                "Body");
        assertThat(sent.subject()).isEqualTo("Hello Bcc: eve@evil.test");
        assertThat(sent.to()).containsExactly("dana.kim@acme.test");
    }

    /** v0.0.11 🍊 recordBlocked stores an outer guard's refusal leniently (sanitized, own sender by default). */
    @Test
    void recordBlockedStoresTheAttempt() {
        AgentProfile agent = liaison(10);
        Email blocked = mailbox.recordBlocked(agent.agentId(), null, List.of("eve@evil.test", "bad\naddress"),
                "Subject\nline", "Body");
        assertThat(blocked.status()).isEqualTo(Email.Status.BLOCKED);
        assertThat(blocked.from()).isEqualTo(mailbox.addressOf(agent.agentId()));
        assertThat(blocked.to()).containsExactly("eve@evil.test", "bad address");
        assertThat(blocked.subject()).isEqualTo("Subject line");
        assertThat(mailbox.read(agent.agentId(), blocked.id()).status()).isEqualTo(Email.Status.BLOCKED);
    }

    /** v0.0.11 🍊 Agent addresses come from citrus names; unknown agents fall back to their id. */
    @Test
    void addressesComeFromCitrusNames() {
        AgentProfile agent = liaison(10);
        assertThat(FakeMailbox.addressOf("Blood Orange")).isEqualTo("blood.orange@citrushq.test");
        assertThat(mailbox.addressOf(agent.agentId()))
                .isEqualTo(agent.name().toLowerCase(Locale.ROOT).replace(' ', '.') + "@citrushq.test");
        assertThat(mailbox.addressOf(AgentId.of("agent-fffe"))).isEqualTo("agent-fffe@citrushq.test");
    }

    /** v0.0.11 🍊 A customer liaison with the default domains and a custom hourly limit. */
    private AgentProfile liaison(int perHour) {
        return agents.create(room, new CreateAgentRequest(Role.CUSTOMER_LIAISON, null, null, null, null,
                new Limits.LimitsPatch(List.of("acme.test", "example.com"), perHour, null, null, null)));
    }

    /** v0.0.11 🍊 Events of a type published for the agent (in its room) after a cursor. */
    private List<JsonNode> events(long after, EventType type, AgentProfile agent) {
        return hub.bufferedEvents().stream()
                .filter(event -> event.id() > after && event.type() == type && event.roomId().equals(agent.roomId()))
                .map(event -> {
                    try {
                        return mapper.readTree(event.json());
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .filter(node -> node.path("agentId").asText().equals(agent.agentId().value()))
                .toList();
    }
}
