package ai.yuzu.trace;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.monitor.EventPhase;
import ai.yuzu.monitor.ModuleEvent;
import ai.yuzu.monitor.ModuleKind;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;

/** v0.0.12 🍊 Maps module_event rows to ModuleEvent records; unknown module or phase names degrade instead of failing. */
final class ModuleEventRows implements RowMapper<ModuleEvent> {

    /** v0.0.12 🍊 Columns every module_event query selects. */
    static final String COLUMNS = """
            agent_id, seq, id, module, phase, text, detail, trace_id, span_id, parent_span_id, created_at""";

    private static final TypeReference<Map<String, Object>> DETAIL = new TypeReference<>() {
    };

    private final Jsons jsons;

    /** v0.0.12 🍊 Binds the JSON helper used for the detail column. */
    ModuleEventRows(Jsons jsons) {
        this.jsons = jsons;
    }

    /** v0.0.12 🍊 Reads one row. */
    @Override
    public ModuleEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ModuleEvent(rs.getString("id"), AgentId.of(rs.getString("agent_id")), module(rs.getString("module")),
                phase(rs.getString("phase")), rs.getString("text"), detail(rs.getString("detail")),
                rs.getString("trace_id"), rs.getString("span_id"), rs.getString("parent_span_id"),
                DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)));
    }

    /** v0.0.12 🍊 Module by name (SYSTEM when the stored name is unknown). */
    private static ModuleKind module(String name) {
        try {
            return ModuleKind.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ModuleKind.SYSTEM;
        }
    }

    /** v0.0.12 🍊 Phase by name (INFO when the stored name is unknown). */
    private static EventPhase phase(String name) {
        try {
            return EventPhase.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return EventPhase.INFO;
        }
    }

    /** v0.0.12 🍊 Detail JSON object (null when absent or unreadable). */
    private Map<String, Object> detail(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return jsons.read(json, DETAIL);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
