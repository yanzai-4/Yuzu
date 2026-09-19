package ai.yuzu.trace;

import ai.yuzu.common.time.DbTime;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.usage.Usage;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * v0.0.30 🍊 Read side of {@code llm_call} for the trace inspector: the attempts of one trace, and one row by id.
 *
 * <p>Deliberately not agent-scoped: a trace crosses agents, exactly like {@link TraceEventRepository}. The
 * trace query rides on {@code KEY k_trace (trace_id)}.</p>
 */
@Repository
public class LlmCallRepository {

    private static final String COLUMNS = """
            agent_id, id, module, tier, model, strategy, attempt, prompt_tokens, cached_tokens, cache_write_tokens,
            completion_tokens, reasoning_tokens, estimated, latency_ms, ttft_ms, status, error, trace_id,
            payload_path, created_at""";

    static final String SQL_BY_TRACE = "SELECT " + COLUMNS
            + " FROM llm_call WHERE trace_id = :traceId ORDER BY created_at, seq LIMIT :limit";
    static final String SQL_BY_ID = "SELECT " + COLUMNS + " FROM llm_call WHERE id = :id";

    private final JdbcClient jdbc;
    private final NaturalTime time;

    /** v0.0.30 🍊 Injects the JDBC client and the clock used for natural-language times. */
    public LlmCallRepository(JdbcClient jdbc, NaturalTime time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    /** v0.0.30 🍊 Every recorded attempt of a trace, oldest first. */
    public List<Row> byTrace(String traceId, int limit) {
        return jdbc.sql(SQL_BY_TRACE).param("traceId", traceId).param("limit", limit).query(rows()).list();
    }

    /** v0.0.30 🍊 One recorded attempt by its record id. */
    public Optional<Row> byId(String callId) {
        return jdbc.sql(SQL_BY_ID).param("id", callId).query(rows()).optional();
    }

    /** v0.0.30 🍊 One llm_call row plus the workspace path of its raw request/response payload. */
    public record Row(LlmCallView view, String payloadPath) {
    }

    /** v0.0.30 🍊 Maps a row to the contract view (the payload path stays server-side). */
    private RowMapper<Row> rows() {
        return (ResultSet rs, int rowNum) -> {
            String payloadPath = rs.getString("payload_path");
            Usage usage = new Usage(rs.getInt("prompt_tokens"), rs.getInt("cached_tokens"),
                    rs.getInt("cache_write_tokens"), rs.getInt("completion_tokens"), rs.getInt("reasoning_tokens"),
                    rs.getBoolean("estimated"), false);
            Long ttft = rs.getObject("ttft_ms") == null ? null : rs.getLong("ttft_ms");
            LlmCallView view = LlmCallView.of(rs.getString("id"), rs.getString("agent_id"), rs.getString("module"),
                    rs.getString("tier"), rs.getString("model"), rs.getString("strategy"), rs.getInt("attempt"),
                    rs.getString("status"), rs.getString("error"), rs.getString("trace_id"), usage,
                    rs.getLong("latency_ms"), ttft, payloadPath != null,
                    time.compact(DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class))));
            return new Row(view, payloadPath);
        };
    }
}
