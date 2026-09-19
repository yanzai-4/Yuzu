package ai.yuzu.sim.email;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.persistence.AgentScopedRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** v0.0.11 🍊 Agent-scoped access to the {@code fake_email} table (clustered by agent_id, seq). */
@Repository
public class EmailRepository extends AgentScopedRepository {

    private static final String COLUMNS =
            "seq, id, agent_id, direction, from_addr, to_addr, subject, body, status, created_at";
    private static final String SQL_INSERT = """
            INSERT INTO fake_email (agent_id, id, direction, from_addr, to_addr, subject, body, status, dedupe_hash,
                created_at)
            VALUES (:agentId, :id, :direction, :from, :to, :subject, :body, :status, :hash, :created)""";
    private static final String SQL_FIND =
            "SELECT " + COLUMNS + " FROM fake_email WHERE agent_id = :agentId AND id = :id";
    private static final String SQL_RECENT = "SELECT " + COLUMNS + " FROM fake_email WHERE agent_id = :agentId"
            + " ORDER BY created_at DESC, seq DESC LIMIT :limit";
    private static final String SQL_INBOX = "SELECT " + COLUMNS + " FROM fake_email WHERE agent_id = :agentId"
            + " AND direction = 'IN' ORDER BY created_at DESC, seq DESC LIMIT :limit";
    private static final String SQL_COUNT_INBOX =
            "SELECT COUNT(*) FROM fake_email WHERE agent_id = :agentId AND direction = 'IN'";
    private static final String SQL_FIND_SENT_DUPLICATE = "SELECT " + COLUMNS + " FROM fake_email"
            + " WHERE agent_id = :agentId AND dedupe_hash = :hash AND direction = 'OUT' AND status = 'SENT'"
            + " AND created_at >= :since ORDER BY seq DESC LIMIT 1";
    private static final String SQL_COUNT_SENT_SINCE = "SELECT COUNT(*) FROM fake_email WHERE agent_id = :agentId"
            + " AND direction = 'OUT' AND status = 'SENT' AND created_at >= :since";

    private static final RowMapper<Email> MAPPER = (rs, i) -> new Email(
            rs.getString("id"), AgentId.of(rs.getString("agent_id")), rs.getLong("seq"),
            Email.Direction.valueOf(rs.getString("direction")), rs.getString("from_addr"),
            splitRecipients(rs.getString("to_addr")), rs.getString("subject"), rs.getString("body"),
            Email.Status.valueOf(rs.getString("status")),
            DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)));

    /** v0.0.11 🍊 Injects the shared JDBC client. */
    public EmailRepository(JdbcClient jdbc) {
        super(jdbc);
    }

    /** v0.0.11 🍊 Stores a draft with a fresh {@code email-<hex>-<10hex>} id; returns it with id and seq. */
    public Email insert(Email draft, byte[] dedupeHash) {
        KeyHolder keys = new GeneratedKeyHolder();
        String id = insertWithFreshId(DataName.EMAIL, draft.agentId(), fresh -> scoped(SQL_INSERT, draft.agentId())
                .param("id", fresh).param("direction", draft.direction().name()).param("from", draft.from())
                .param("to", draft.toLine()).param("subject", draft.subject()).param("body", draft.body())
                .param("status", draft.status().name()).param("hash", dedupeHash)
                .param("created", DbTime.toDb(draft.createdAt()))
                .update(keys, "seq"));
        return draft.stored(id, keys.getKey().longValue());
    }

    /** v0.0.11 🍊 One e-mail of the agent (never another agent's). */
    public Optional<Email> find(AgentId agentId, String id) {
        return scoped(SQL_FIND, agentId).param("id", id).query(MAPPER).optional();
    }

    /** v0.0.11 🍊 The agent's latest e-mails (both directions), newest first. */
    public List<Email> recent(AgentId agentId, int limit) {
        return scoped(SQL_RECENT, agentId).param("limit", limit).query(MAPPER).list();
    }

    /** v0.0.11 🍊 The agent's received e-mails, newest first. */
    public List<Email> inbox(AgentId agentId, int limit) {
        return scoped(SQL_INBOX, agentId).param("limit", limit).query(MAPPER).list();
    }

    /** v0.0.11 🍊 Number of received e-mails (0 means the inbox was never seeded). */
    public int countInbox(AgentId agentId) {
        return scoped(SQL_COUNT_INBOX, agentId).query(Integer.class).single();
    }

    /** v0.0.11 🍊 The latest SENT e-mail with the same dedupe hash created at or after {@code since}. */
    public Optional<Email> findSentDuplicate(AgentId agentId, byte[] hash, Instant since) {
        return scoped(SQL_FIND_SENT_DUPLICATE, agentId).param("hash", hash).param("since", DbTime.toDb(since))
                .query(MAPPER).optional();
    }

    /** v0.0.11 🍊 Number of e-mails actually SENT at or after {@code since} (blocked attempts do not count). */
    public int countSentSince(AgentId agentId, Instant since) {
        return scoped(SQL_COUNT_SENT_SINCE, agentId).param("since", DbTime.toDb(since)).query(Integer.class).single();
    }

    /** v0.0.11 🍊 Parses the stored comma-separated recipient line. */
    private static List<String> splitRecipients(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        return Arrays.stream(line.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
