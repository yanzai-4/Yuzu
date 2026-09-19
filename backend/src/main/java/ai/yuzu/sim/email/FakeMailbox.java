package ai.yuzu.sim.email;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.Permission;
import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** v0.0.11 🍊 Simulated mail server: seeded inboxes, idempotent sends guarded by the e-mail rules, sim.email events. */
@Service
public class FakeMailbox {

    /** v0.0.11 🍊 An identical send (same agent, recipients, subject and body) within this window is not repeated. */
    public static final Duration DEDUPE_WINDOW = Duration.ofMinutes(10);

    /** v0.0.11 🍊 Window of the per-agent hourly send limit. */
    public static final Duration RATE_WINDOW = Duration.ofHours(1);

    /** v0.0.11 🍊 Domain of the agents' own addresses (for example pomelo@citrushq.test). */
    public static final String COMPANY_DOMAIN = "citrushq.test";

    /** v0.0.11 🍊 Longest subject (column limit). */
    public static final int MAX_SUBJECT_CHARS = 300;

    /** v0.0.11 🍊 Longest body accepted from an agent. */
    public static final int MAX_BODY_CHARS = 100_000;

    /** v0.0.11 🍊 Longest comma-separated recipient line (column limit). */
    public static final int MAX_RECIPIENT_LINE_CHARS = 500;

    private static final int MAX_FROM_CHARS = 200;
    private static final int INBOX_LIMIT = 100;
    private static final int MAX_LIST_LIMIT = 500;

    private final EmailRepository repository;
    private final AgentService agents;
    private final SseHub hub;
    private final NaturalTime time;
    private final TransactionTemplate tx;
    private final Map<AgentId, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Set<AgentId> seeded = ConcurrentHashMap.newKeySet();

    /** v0.0.11 🍊 Injects collaborators (the clock comes from NaturalTime so tests can move time). */
    public FakeMailbox(EmailRepository repository, AgentService agents, SseHub hub, NaturalTime time,
                       TransactionTemplate tx) {
        this.repository = repository;
        this.agents = agents;
        this.hub = hub;
        this.time = time;
        this.tx = tx;
    }

    /** v0.0.11 🍊 The agent's own address, derived from its citrus name (for example "blood.orange@citrushq.test"). */
    public String addressOf(AgentId agentId) {
        return agents.find(agentId).map(profile -> addressOf(profile.name()))
                .orElseGet(() -> agentId.value() + "@" + COMPANY_DOMAIN);
    }

    /** v0.0.11 🍊 Received e-mails, newest first; the first access seeds five realistic customer e-mails. */
    public List<Email> inbox(AgentId agentId) {
        ensureSeeded(agentId);
        return repository.inbox(agentId, INBOX_LIMIT);
    }

    /** v0.0.11 🍊 One e-mail of this agent's mailbox (NOT_FOUND for unknown ids and other agents' e-mails). */
    public Email read(AgentId agentId, String emailId) {
        if (emailId == null || emailId.isBlank()) {
            throw badRequest(agentId, "An e-mail id is required.");
        }
        return repository.find(agentId, emailId.strip()).orElseThrow(() -> (NotFoundException)
                new NotFoundException("There is no e-mail " + EmailRules.printable(emailId.strip())
                        + " in this mailbox.").with("emailId", emailId.strip()).forAgent(agentId.value()));
    }

    /** v0.0.11 🍊 Sends once per 10 minutes (a duplicate returns the first); broken rules record BLOCKED and throw. */
    public Email send(AgentId agentId, String from, List<String> to, String subject, String body) {
        AgentProfile profile = agents.require(agentId);
        String cleanSubject = requireSubject(agentId, subject);
        String cleanBody = requireBody(agentId, body);
        List<String> recipients = EmailRules.normalize(to);
        if (String.join(", ", recipients).length() > MAX_RECIPIENT_LINE_CHARS) {
            throw badRequest(agentId, "The recipient list is too long (at most " + MAX_RECIPIENT_LINE_CHARS
                    + " characters).");
        }
        String own = addressOf(profile.name());
        String sender = from == null || from.isBlank() ? own : from.strip().toLowerCase(Locale.ROOT);
        byte[] hash = dedupeHash(agentId, recipients, cleanSubject, cleanBody);
        Email email;
        List<EmailRules.Violation> violations;
        ReentrantLock lock = lockFor(agentId);
        lock.lock();
        try {
            Instant now = time.nowInstant();
            violations = identityViolations(profile, own, sender);
            Optional<Email> duplicate = repository.findSentDuplicate(agentId, hash, now.minus(DEDUPE_WINDOW));
            if (duplicate.isPresent() && violations.isEmpty()) {
                return duplicate.get();
            }
            int sentLastHour = repository.countSentSince(agentId, now.minus(RATE_WINDOW));
            violations.addAll(EmailRules.check(profile.scope().limits(), recipients, sentLastHour).violations());
            email = violations.isEmpty()
                    ? repository.insert(Email.draft(agentId, Email.Direction.OUT, own, recipients, cleanSubject,
                    cleanBody, Email.Status.SENT, now), hash)
                    : repository.insert(Email.draft(agentId, Email.Direction.OUT, safeSender(sender),
                    fitRecipients(recipients), cleanSubject, cleanBody, Email.Status.BLOCKED, now), null);
        } finally {
            lock.unlock();
        }
        publish(email);
        if (!violations.isEmpty()) {
            throw blocked(email, violations);
        }
        return email;
    }

    /** v0.0.11 🍊 Records an outgoing e-mail that a guard refused (status BLOCKED) so the UI shows the attempt. */
    public Email recordBlocked(AgentId agentId, String from, List<String> to, String subject, String body) {
        String sender = from == null || from.isBlank() ? addressOf(agentId) : from;
        String cleanSubject = clip(controlsToSpaces(subject == null ? "" : subject).strip(), MAX_SUBJECT_CHARS);
        String cleanBody = clip(body == null ? "" : body, MAX_BODY_CHARS);
        Email email = repository.insert(Email.draft(agentId, Email.Direction.OUT, safeSender(sender),
                fitRecipients(EmailRules.normalize(to)), cleanSubject, cleanBody, Email.Status.BLOCKED,
                time.nowInstant()), null);
        publish(email);
        return email;
    }

    /** v0.0.11 🍊 Number of e-mails the agent actually sent at or after {@code since} (input of the hourly rule). */
    public int countSentSince(AgentId agentId, Instant since) {
        return repository.countSentSince(agentId, since);
    }

    /** v0.0.11 🍊 The agent's latest e-mails in both directions, newest first (no seeding). */
    public List<Email> emails(AgentId agentId, int limit) {
        return repository.recent(agentId, Math.max(1, Math.min(limit, MAX_LIST_LIMIT)));
    }

    /** v0.0.11 🍊 SHA-256 over agent, sorted recipients, subject and body (length-prefixed, so fields cannot blur). */
    static byte[] dedupeHash(AgentId agentId, List<String> recipients, String subject, String body) {
        String canonical = agentId.value() + "\n" + String.join(",", recipients.stream().sorted().toList()) + "\n"
                + subject.length() + ":" + subject + "\n" + body.length() + ":" + body;
        try {
            return MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** v0.0.11 🍊 Address for a citrus name: lowercase, non-alphanumerics become dots. */
    static String addressOf(String citrusName) {
        String local = citrusName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "");
        return (local.isEmpty() ? "agent" : local) + "@" + COMPANY_DOMAIN;
    }

    /** v0.0.11 🍊 Who may send (permission, not retired, own sender address); checked even for duplicates. */
    private static List<EmailRules.Violation> identityViolations(AgentProfile profile, String own, String sender) {
        List<EmailRules.Violation> found = new ArrayList<>();
        if (!profile.isPresent()) {
            found.add(new EmailRules.Violation(EmailRules.Code.MISSING_PERMISSION, "Retired agents cannot send e-mail."));
        } else if (!profile.scope().has(Permission.EMAIL_SEND)) {
            found.add(new EmailRules.Violation(EmailRules.Code.MISSING_PERMISSION,
                    "This agent does not have the EMAIL_SEND permission."));
        }
        EmailRules.checkSender(own, sender).ifPresent(found::add);
        return found;
    }

    /** v0.0.11 🍊 Seeds the inbox once per agent (checked under the agent lock, inserted in one transaction). */
    private void ensureSeeded(AgentId agentId) {
        if (seeded.contains(agentId)) {
            return;
        }
        List<Email> created = List.of();
        ReentrantLock lock = lockFor(agentId);
        lock.lock();
        try {
            if (!seeded.contains(agentId)) {
                if (repository.countInbox(agentId) == 0) {
                    created = seed(agentId);
                }
                seeded.add(agentId);
            }
        } finally {
            lock.unlock();
        }
        created.forEach(this::publish);
    }

    /** v0.0.11 🍊 Inserts the seed e-mails, back-dated to when they "arrived". */
    private List<Email> seed(AgentId agentId) {
        String own = addressOf(agentId);
        Instant now = time.nowInstant();
        List<Email> created = tx.execute(status -> InboxSeed.emails().stream()
                .map(seed -> repository.insert(Email.draft(agentId, Email.Direction.IN, seed.from(), List.of(own),
                        seed.subject(), seed.body(), Email.Status.RECEIVED, now.minus(seed.age())), null))
                .toList());
        return created == null ? List.of() : created;
    }

    /** v0.0.11 🍊 Publishes sim.email to the agent's room (every room when the agent is unknown). */
    private void publish(Email email) {
        Optional<String> roomId = agents.find(email.agentId()).map(AgentProfile::roomId);
        EmailView view = email.toView(time);
        if (roomId.isPresent()) {
            hub.publish(roomId.get(), EventType.SIM_EMAIL, email.agentId().value(), view);
        } else {
            hub.publishAll(EventType.SIM_EMAIL, email.agentId().value(), view);
        }
    }

    /** v0.0.11 🍊 PERMISSION_DENIED carrying the recorded e-mail id, the reasons and their codes. */
    private static PermissionDeniedException blocked(Email email, List<EmailRules.Violation> violations) {
        List<String> reasons = violations.stream().map(EmailRules.Violation::message).toList();
        List<String> codes = violations.stream().map(v -> v.code().name()).toList();
        return (PermissionDeniedException) new PermissionDeniedException("E-mail blocked: " + String.join(" ", reasons))
                .with("emailId", email.id()).with("reasons", reasons).with("codes", codes)
                .with("recipients", email.to()).forAgent(email.agentId().value());
    }

    /** v0.0.11 🍊 Subject on one line (controls become spaces), required and at most 300 characters. */
    private static String requireSubject(AgentId agentId, String subject) {
        String clean = controlsToSpaces(subject == null ? "" : subject).replaceAll(" {2,}", " ").strip();
        if (clean.isEmpty()) {
            throw badRequest(agentId, "An e-mail needs a subject.");
        }
        if (clean.length() > MAX_SUBJECT_CHARS) {
            throw badRequest(agentId, "The subject can have at most " + MAX_SUBJECT_CHARS + " characters.");
        }
        return clean;
    }

    /** v0.0.11 🍊 Body without trailing whitespace, required and at most 100,000 characters. */
    private static String requireBody(AgentId agentId, String body) {
        String clean = body == null ? "" : body.stripTrailing();
        if (clean.isBlank()) {
            throw badRequest(agentId, "An e-mail needs a body.");
        }
        if (clean.length() > MAX_BODY_CHARS) {
            throw badRequest(agentId, "The body can have at most " + MAX_BODY_CHARS + " characters.");
        }
        return clean;
    }

    /** v0.0.11 🍊 Recipients of a BLOCKED record: controls removed, trimmed to fit the 500-character column. */
    private static List<String> fitRecipients(List<String> recipients) {
        List<String> kept = new ArrayList<>();
        int length = 0;
        for (String recipient : recipients) {
            String address = clip(controlsToSpaces(recipient).strip(), EmailRules.MAX_ADDRESS_CHARS);
            int added = (kept.isEmpty() ? 0 : 2) + address.length();
            if (address.isEmpty() || length + added > MAX_RECIPIENT_LINE_CHARS) {
                continue;
            }
            kept.add(address);
            length += added;
        }
        return kept;
    }

    /** v0.0.11 🍊 Sender of a BLOCKED record: controls removed, at most 200 characters. */
    private static String safeSender(String sender) {
        return clip(controlsToSpaces(sender).strip(), MAX_FROM_CHARS);
    }

    /** v0.0.11 🍊 Replaces control and line-separator characters (header injection) with spaces. */
    private static String controlsToSpaces(String value) {
        return value.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", " ");
    }

    /** v0.0.11 🍊 Cuts a string to at most max characters. */
    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** v0.0.11 🍊 BAD_REQUEST attributed to the agent. */
    private static BadRequestException badRequest(AgentId agentId, String message) {
        return (BadRequestException) new BadRequestException(message).forAgent(agentId.value());
    }

    /** v0.0.11 🍊 The agent's mailbox lock (serializes dedupe checks, rate counts and seeding). */
    private ReentrantLock lockFor(AgentId agentId) {
        return locks.computeIfAbsent(agentId, id -> new ReentrantLock());
    }
}
