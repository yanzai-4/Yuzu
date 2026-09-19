package ai.yuzu.internal.consciousness;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.persistence.AgentScopedRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/** v0.0.12 🍊 Persists pool messages (trace, audit and recovery) in the agent-scoped {@code pool_message} table. */
@Repository
public class PoolMessageRepository extends AgentScopedRepository {

    static final String SQL_INSERT = """
            INSERT INTO pool_message (agent_id, id, origin, attribution, text, trace_id, causal_depth,
                emitted_by_main, created_at)
            VALUES (:agentId, :id, :origin, :attribution, :text, :trace, :depth, :emitted, :created)
            """;
    static final String SQL_CONSUME = """
            UPDATE pool_message SET run_id = :runId, consumed_at = :consumed
            WHERE agent_id = :agentId AND id IN (:ids)
            """;

    /** v0.0.12 🍊 Injects the JDBC client. */
    public PoolMessageRepository(JdbcClient jdbc) {
        super(jdbc);
    }

    /** v0.0.12 🍊 Generates an id and stores a message; returns the stored message. */
    public PoolMessage insert(AgentId agentId, Origin origin, String attribution, String text, String traceId,
                              int causalDepth, boolean emittedByMain, Instant createdAt) {
        String id = insertWithFreshId(DataName.POOL, agentId, fresh -> scoped(SQL_INSERT, agentId)
                .param("id", fresh).param("origin", origin.name())
                .param("attribution", attribution.length() > 500 ? attribution.substring(0, 500) : attribution)
                .param("text", text).param("trace", traceId).param("depth", causalDepth)
                .param("emitted", emittedByMain).param("created", DbTime.toDb(createdAt)).update());
        return new PoolMessage(id, agentId, origin, attribution, text, traceId, causalDepth, emittedByMain, createdAt);
    }

    /** v0.0.12 🍊 Marks a drained batch as consumed by a main run. */
    public void markConsumed(AgentId agentId, List<String> ids, String runId, Instant when) {
        if (ids.isEmpty()) {
            return;
        }
        scoped(SQL_CONSUME, agentId).param("runId", runId).param("consumed", DbTime.toDb(when))
                .param("ids", ids).update();
    }
}
