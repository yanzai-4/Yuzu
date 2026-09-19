package ai.yuzu.internal.memory;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.persistence.AgentScopedRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** v0.0.13 🍊 Agent-scoped storage of working-memory entries and the rolling digest. */
@Repository
public class WorkingMemoryRepository extends AgentScopedRepository {

    static final String SQL_INSERT = """
            INSERT INTO working_memory_entry (agent_id, id, run_id, direction, origin, origin_ref, text, tokens,
                compacted, created_at)
            VALUES (:agentId, :id, :runId, :direction, :origin, :originRef, :text, :tokens, 0, :created)
            """;
    static final String SQL_VERBATIM = """
            SELECT agent_id, seq, id, run_id, direction, origin, origin_ref, text, tokens, compacted, created_at
            FROM working_memory_entry WHERE agent_id = :agentId AND compacted = 0 ORDER BY seq
            """;
    static final String SQL_MARK_COMPACTED = """
            UPDATE working_memory_entry SET compacted = 1
            WHERE agent_id = :agentId AND compacted = 0 AND seq <= :upTo
            """;
    static final String SQL_ARCHIVED_RANGE = """
            SELECT agent_id, seq, id, run_id, direction, origin, origin_ref, text, tokens, compacted, created_at
            FROM working_memory_entry
            WHERE agent_id = :agentId AND created_at BETWEEN :from AND :to ORDER BY seq LIMIT :limit
            """;
    static final String SQL_DIGEST = """
            SELECT summary FROM working_memory_digest WHERE agent_id = :agentId
            """;
    static final String SQL_UPSERT_DIGEST = """
            INSERT INTO working_memory_digest (agent_id, id, summary, covers_through_seq, version, updated_at)
            VALUES (:agentId, :id, :summary, :through, 0, :updated)
            ON DUPLICATE KEY UPDATE summary = VALUES(summary), covers_through_seq = VALUES(covers_through_seq),
                version = version + 1, updated_at = VALUES(updated_at)
            """;

    private static final RowMapper<WorkingMemoryEntry> MAPPER = (rs, i) -> new WorkingMemoryEntry(
            rs.getString("id"), rs.getString("agent_id"), rs.getLong("seq"), rs.getString("run_id"),
            WorkingMemoryEntry.Direction.valueOf(rs.getString("direction")), rs.getString("origin"),
            rs.getString("origin_ref"), rs.getString("text"), rs.getInt("tokens"), rs.getBoolean("compacted"),
            DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)));

    /** v0.0.13 🍊 Injects the JDBC client. */
    public WorkingMemoryRepository(JdbcClient jdbc) {
        super(jdbc);
    }

    /**
     * v0.0.13 🍊 Inserts an entry unless one with the same origin reference exists (idempotent recording).
     *
     * @return the stored entry, or empty when it was a duplicate
     */
    public Optional<WorkingMemoryEntry> insertIfAbsent(AgentId agentId, String runId,
                                                       WorkingMemoryEntry.Direction direction, String source,
                                                       String originRef, String text, int tokens, Instant created) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String id = IdGen.recordId(DataName.WORKING_MEMORY, agentId);
            KeyHolder keys = new GeneratedKeyHolder();
            try {
                scoped(SQL_INSERT, agentId).param("id", id).param("runId", runId).param("direction", direction.name())
                        .param("origin", source.length() > 16 ? source.substring(0, 16) : source)
                        .param("originRef", originRef).param("text", text).param("tokens", tokens)
                        .param("created", DbTime.toDb(created)).update(keys, "seq");
                return Optional.of(new WorkingMemoryEntry(id, agentId.value(), keys.getKey().longValue(), runId,
                        direction, source, originRef, text, tokens, false, created));
            } catch (DuplicateKeyException e) {
                if (String.valueOf(e.getMessage()).contains("uk_origin")) {
                    return Optional.empty();
                }
            }
        }
        throw new IllegalStateException("Could not allocate a working-memory id");
    }

    /** v0.0.13 🍊 Verbatim (not yet compacted) entries, oldest first. */
    public List<WorkingMemoryEntry> verbatim(AgentId agentId) {
        return scoped(SQL_VERBATIM, agentId).query(MAPPER).list();
    }

    /** v0.0.13 🍊 Marks every verbatim entry up to a sequence number as compacted. */
    public void markCompacted(AgentId agentId, long upToSeq) {
        scoped(SQL_MARK_COMPACTED, agentId).param("upTo", upToSeq).update();
    }

    /** v0.0.13 🍊 Entries (compacted or not) created within a time range, for time-based recall. */
    public List<WorkingMemoryEntry> inRange(AgentId agentId, Instant from, Instant to, int limit) {
        return scoped(SQL_ARCHIVED_RANGE, agentId).param("from", DbTime.toDb(from)).param("to", DbTime.toDb(to))
                .param("limit", limit).query(MAPPER).list();
    }

    /** v0.0.13 🍊 Current digest text (empty when none). */
    public String digest(AgentId agentId) {
        return scoped(SQL_DIGEST, agentId).query(String.class).optional().orElse("");
    }

    /** v0.0.13 🍊 Stores the digest covering entries up to a sequence number. */
    public void saveDigest(AgentId agentId, String summary, long throughSeq, Instant now) {
        scoped(SQL_UPSERT_DIGEST, agentId).param("id", IdGen.recordId(DataName.WORKING_DIGEST, agentId))
                .param("summary", summary).param("through", throughSeq).param("updated", DbTime.toDb(now)).update();
    }
}
