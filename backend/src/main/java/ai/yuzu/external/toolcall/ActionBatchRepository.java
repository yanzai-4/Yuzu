package ai.yuzu.external.toolcall;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.persistence.AgentScopedRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/** v0.0.18 🍊 Persists action batches ({@code action_batch}, agent-scoped): review verdicts and final status. */
@Repository
public class ActionBatchRepository extends AgentScopedRepository {

    static final String SQL_INSERT = """
            INSERT INTO action_batch (agent_id, id, run_id, lineage_id, actions, status, trace_id, created_at, updated_at)
            VALUES (:agentId, :id, :runId, :lineage, :actions, 'REVIEWING', :trace, :now, :now)
            """;
    static final String SQL_STATUS = """
            UPDATE action_batch SET status = :status, review = :review, warning = :warning, updated_at = :now
            WHERE agent_id = :agentId AND id = :id
            """;

    private final Jsons jsons;

    /** v0.0.18 🍊 Injects collaborators. */
    public ActionBatchRepository(JdbcClient jdbc, Jsons jsons) {
        super(jdbc);
        this.jsons = jsons;
    }

    /** v0.0.18 🍊 Stores a new batch in REVIEWING state. */
    public void insert(AgentId agentId, String batchId, String runId, List<String> actions, String traceId, Instant now) {
        scoped(SQL_INSERT, agentId).param("id", batchId).param("runId", runId).param("lineage", batchId)
                .param("actions", jsons.write(actions)).param("trace", traceId).param("now", DbTime.toDb(now)).update();
    }

    /** v0.0.18 🍊 Updates the batch status with the review (as JSON) and the warning. */
    public void status(AgentId agentId, String batchId, String status, Object review, String warning, Instant now) {
        scoped(SQL_STATUS, agentId).param("id", batchId).param("status", status)
                .param("review", review == null ? null : jsons.write(review)).param("warning", warning)
                .param("now", DbTime.toDb(now)).update();
    }
}
