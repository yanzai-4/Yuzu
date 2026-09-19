package ai.yuzu.task.list;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.persistence.AgentScopedRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** v0.0.10 🍊 Agent-scoped access to the {@code task_item} table (every statement is bound to :agentId). */
@Repository
public class TaskItemRepository extends AgentScopedRepository {

    private static final String COLUMNS = "agent_id, id, list_id, ord, text, state, note, struck_reason, created_at,"
            + " updated_at";
    private static final String SQL_FIND_BY_LIST = "SELECT " + COLUMNS + " FROM task_item WHERE agent_id = :agentId"
            + " AND list_id = :listId ORDER BY ord";
    private static final String SQL_FIND_BY_LISTS = "SELECT " + COLUMNS + " FROM task_item WHERE agent_id = :agentId"
            + " AND list_id IN (:listIds) ORDER BY list_id, ord";
    private static final String SQL_INSERT = """
            INSERT INTO task_item (agent_id, id, list_id, ord, text, state, note, struck_reason, created_at, updated_at)
            VALUES (:agentId, :id, :listId, :ord, :text, :state, :note, :struckReason, :created, :updated)
            """;
    private static final String SQL_UPDATE = """
            UPDATE task_item SET state = :state, note = :note, struck_reason = :struckReason, updated_at = :updated
            WHERE agent_id = :agentId AND list_id = :listId AND id = :id
            """;

    private static final RowMapper<TaskItemRow> MAPPER = (rs, i) -> new TaskItemRow(
            AgentId.of(rs.getString("agent_id")), rs.getString("id"), rs.getString("list_id"), rs.getInt("ord"),
            rs.getString("text"), TaskItemState.valueOf(rs.getString("state")), rs.getString("note"),
            rs.getString("struck_reason"),
            DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)),
            DbTime.fromDb(rs.getObject("updated_at", LocalDateTime.class)));

    /** v0.0.10 🍊 Injects the JDBC client. */
    public TaskItemRepository(JdbcClient jdbc) {
        super(jdbc);
    }

    /** v0.0.10 🍊 Items of one list in display order. */
    public List<TaskItemRow> findByList(AgentId agentId, String listId) {
        return scoped(SQL_FIND_BY_LIST, agentId).param("listId", listId).query(MAPPER).list();
    }

    /** v0.0.10 🍊 Items of several lists, grouped by list id, each group in display order. */
    public Map<String, List<TaskItemRow>> findByLists(AgentId agentId, Collection<String> listIds) {
        if (listIds.isEmpty()) {
            return Map.of();
        }
        return scoped(SQL_FIND_BY_LISTS, agentId).param("listIds", listIds).query(MAPPER).list().stream()
                .collect(Collectors.groupingBy(TaskItemRow::listId, LinkedHashMap::new, Collectors.toList()));
    }

    /** v0.0.10 🍊 Inserts an item (its list id must be set) with a fresh {@code item-<agentHex>-<10hex>} id. */
    public String insert(TaskItemRow row) {
        return insertWithFreshId(DataName.TASK_ITEM, row.agentId(), id -> scoped(SQL_INSERT, row.agentId())
                .param("id", id).param("listId", row.listId()).param("ord", row.ord()).param("text", row.text())
                .param("state", row.state().name()).param("note", row.note())
                .param("struckReason", row.struckReason())
                .param("created", DbTime.toDb(row.createdAt())).param("updated", DbTime.toDb(row.updatedAt()))
                .update());
    }

    /** v0.0.10 🍊 Writes an item's state, note and strike reason (text and ord never change). */
    public void update(TaskItemRow row) {
        scoped(SQL_UPDATE, row.agentId())
                .param("state", row.state().name()).param("note", row.note())
                .param("struckReason", row.struckReason()).param("updated", DbTime.toDb(row.updatedAt()))
                .param("listId", row.listId()).param("id", row.id())
                .update();
    }
}
