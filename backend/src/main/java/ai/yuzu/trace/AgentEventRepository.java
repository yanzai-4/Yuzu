package ai.yuzu.trace;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.monitor.ModuleEvent;
import ai.yuzu.persistence.AgentScopedRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
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

    /** v0.0.26 🍊 One page plus the seq of its oldest row, so a client can ask for the next page without seeing seq. */
    public Page page(AgentId agentId, Long beforeSeq, int limit) {
        List<Long> seqs = new ArrayList<>();
        List<ModuleEvent> events = (beforeSeq == null
                ? scoped(SQL_LATEST, agentId)
                : scoped(SQL_BEFORE, agentId).param("beforeSeq", beforeSeq))
                .param("limit", limit)
                .query((rs, rowNum) -> {
                    seqs.add(rs.getLong("seq"));
                    return rows.mapRow(rs, rowNum);
                })
                .list();
        return new Page(events, seqs.isEmpty() ? null : seqs.get(seqs.size() - 1));
    }

    /** v0.0.26 🍊 A page of events and the seq that pages to the next (older) one; null when the page is empty. */
    public record Page(List<ModuleEvent> events, Long oldestSeq) {
    }
}
