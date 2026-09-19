package ai.yuzu.internal.consciousness;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.persistence.AgentScopedRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/** v0.0.17 🍊 Persists every main-consciousness run ({@code main_run}, agent-scoped) for tracing and audit. */
@Repository
public class MainRunRepository extends AgentScopedRepository {

    static final String SQL_INSERT = """
            INSERT INTO main_run (agent_id, id, mode, thought, actions, next_thought, input_ids, trace_id,
                started_at, ended_at)
            VALUES (:agentId, :id, :mode, :thought, :actions, :next, :inputs, :trace, :started, :ended)
            """;

    private final Jsons jsons;

    /** v0.0.17 🍊 Injects collaborators. */
    public MainRunRepository(JdbcClient jdbc, Jsons jsons) {
        super(jdbc);
        this.jsons = jsons;
    }

    /** v0.0.17 🍊 Stores one run under a pre-generated id. */
    public void insert(AgentId agentId, String runId, String mode, String thought, List<String> actions,
                       String nextThought, List<String> inputIds, String traceId, Instant started, Instant ended) {
        scoped(SQL_INSERT, agentId).param("id", runId).param("mode", mode).param("thought", thought)
                .param("actions", jsons.write(actions)).param("next", nextThought)
                .param("inputs", jsons.write(inputIds)).param("trace", traceId)
                .param("started", DbTime.toDb(started)).param("ended", DbTime.toDb(ended)).update();
    }
}
