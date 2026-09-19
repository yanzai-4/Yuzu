package ai.yuzu.trace;

import ai.yuzu.common.json.Jsons;
import ai.yuzu.monitor.ModuleEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** v0.0.12 🍊 Cross-agent read of one trace (a trace may span several agents), deliberately not agent-scoped. */
@Repository
public class TraceEventRepository {

    static final String SQL_BY_TRACE = "SELECT " + ModuleEventRows.COLUMNS
            + " FROM module_event WHERE trace_id = :traceId ORDER BY created_at, seq LIMIT :limit";

    private final JdbcClient jdbc;
    private final ModuleEventRows rows;

    /** v0.0.12 🍊 Injects the JDBC client and JSON helper. */
    public TraceEventRepository(JdbcClient jdbc, Jsons jsons) {
        this.jdbc = jdbc;
        this.rows = new ModuleEventRows(jsons);
    }

    /** v0.0.12 🍊 Events of a trace in time order (ties broken by insertion order), at most {@code limit}. */
    public List<ModuleEvent> byTrace(String traceId, int limit) {
        return jdbc.sql(SQL_BY_TRACE).param("traceId", traceId).param("limit", limit).query(rows).list();
    }
}
