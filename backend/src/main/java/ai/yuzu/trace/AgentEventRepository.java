package ai.yuzu.trace;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.monitor.ModuleEvent;
import ai.yuzu.persistence.AgentScopedRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** v0.0.12 🍊 Agent-scoped reads of module_event: one agent's history, newest first (clustered range scans). */
@Repository
public class AgentEventRepository extends AgentScopedRepository {

    static final String SQL_LATEST = "SELECT " + ModuleEventRows.COLUMNS
            + " FROM module_event WHERE agent_id = :agentId ORDER BY seq DESC LIMIT :limit";
    static final String SQL_BEFORE = "SELECT " + ModuleEventRows.COLUMNS
            + " FROM module_event WHERE agent_id = :agentId AND seq < :beforeSeq ORDER BY seq DESC LIMIT :limit";

    private final ModuleEventRows rows;

    /** v0.0.12 🍊 Injects the JDBC client and JSON helper. */
    public AgentEventRepository(JdbcClient jdbc, Jsons jsons) {
        super(jdbc);
        this.rows = new ModuleEventRows(jsons);
    }

    /** v0.0.12 🍊 The agent's latest events, newest first. */
    public List<ModuleEvent> latest(AgentId agentId, int limit) {
        return scoped(SQL_LATEST, agentId).param("limit", limit).query(rows).list();
    }

    /** v0.0.12 🍊 The agent's events older than {@code beforeSeq}, newest first. */
    public List<ModuleEvent> before(AgentId agentId, long beforeSeq, int limit) {
        return scoped(SQL_BEFORE, agentId).param("beforeSeq", beforeSeq).param("limit", limit).query(rows).list();
    }
}
