package ai.yuzu.external.toolcall;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.persistence.AgentScopedRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/** v0.0.18 🍊 Persists every tool call ({@code tool_call}, agent-scoped) with its status and a result preview. */
@Repository
public class ToolCallRepository extends AgentScopedRepository {

    static final String SQL_INSERT = """
            INSERT INTO tool_call (agent_id, id, batch_id, action_index, tool, instruction, args, status, trace_id,
                started_at)
            VALUES (:agentId, :id, :batchId, :idx, :tool, :instruction, :args, 'RUNNING', :trace, :started)
            """;
    static final String SQL_FINISH = """
            UPDATE tool_call SET status = :status, trusted = :trusted, result_preview = :preview, error = :error,
                completed_at = :completed
            WHERE agent_id = :agentId AND id = :id
            """;

    /** v0.0.18 🍊 Injects the JDBC client. */
    public ToolCallRepository(JdbcClient jdbc) {
        super(jdbc);
    }

    /** v0.0.18 🍊 Stores a started call. */
    public void started(AgentId agentId, String id, String batchId, int actionIndex, String tool, String instruction,
                        String argsJson, String traceId, Instant started) {
        scoped(SQL_INSERT, agentId).param("id", id).param("batchId", batchId).param("idx", actionIndex)
                .param("tool", tool).param("instruction", instruction)
                .param("args", argsJson == null || argsJson.isBlank() ? "{}" : argsJson)
                .param("trace", traceId).param("started", DbTime.toDb(started)).update();
    }

    /** v0.0.18 🍊 Stores the outcome (preview truncated to 2 KB). */
    public void finished(AgentId agentId, String id, String status, boolean trusted, String output, String error,
                         Instant completed) {
        scoped(SQL_FINISH, agentId).param("id", id).param("status", status).param("trusted", trusted)
                .param("preview", output == null ? null : output.length() > 2_000 ? output.substring(0, 2_000) : output)
                .param("error", error).param("completed", DbTime.toDb(completed)).update();
    }
}
