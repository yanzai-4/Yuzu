package ai.yuzu.task.list;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.persistence.AgentScopedRepository;
import ai.yuzu.task.Actor;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** v0.0.10 🍊 Agent-scoped access to the {@code task_list} table (every statement is bound to :agentId). */
@Repository
public class TaskListRepository extends AgentScopedRepository {

    private static final TypeReference<List<GoalChange>> GOAL_HISTORY = new TypeReference<>() {
    };
    private static final String COLUMNS = """
            agent_id, id, goal, goal_history, status, publisher_kind, publisher_id, publisher_name, ticket_id, outcome,
            approved_by, version, created_at, updated_at, completed_at, approved_at, archived_at""";
    private static final String SQL_FIND_OPEN = "SELECT " + COLUMNS + " FROM task_list WHERE agent_id = :agentId"
            + " AND status IN ('ACTIVE', 'AWAITING_APPROVAL') ORDER BY seq DESC LIMIT 1";
    private static final String SQL_FIND_BY_ID = "SELECT " + COLUMNS + " FROM task_list WHERE agent_id = :agentId"
            + " AND id = :id";
    private static final String SQL_FIND_ARCHIVED = "SELECT " + COLUMNS + " FROM task_list WHERE agent_id = :agentId"
            + " AND status = 'ARCHIVED' ORDER BY archived_at DESC, seq DESC LIMIT :limit";
    private static final String SQL_INSERT = """
            INSERT INTO task_list (agent_id, id, goal, goal_history, status, publisher_kind, publisher_id,
                publisher_name, ticket_id, version, created_at, updated_at)
            VALUES (:agentId, :id, :goal, :history, :status, :publisherKind, :publisherId, :publisherName, :ticketId, 0,
                :created, :updated)
            """;
    private static final String SQL_UPDATE = """
            UPDATE task_list SET goal = :goal, goal_history = :history, status = :status, ticket_id = :ticketId,
                outcome = :outcome, approved_by = :approvedBy, version = version + 1, updated_at = :updated,
                completed_at = :completed, approved_at = :approved, archived_at = :archived
            WHERE agent_id = :agentId AND id = :id AND version = :version
            """;

    private final Jsons jsons;
    private final RowMapper<TaskListRow> mapper;

    /** v0.0.10 🍊 Injects the JDBC client and the JSON helper (goal history column). */
    public TaskListRepository(JdbcClient jdbc, Jsons jsons) {
        super(jdbc);
        this.jsons = jsons;
        this.mapper = (rs, i) -> new TaskListRow(
                AgentId.of(rs.getString("agent_id")), rs.getString("id"), rs.getString("goal"),
                readHistory(rs.getString("goal_history")), TaskListStatus.valueOf(rs.getString("status")),
                Actor.fromColumns(rs.getString("publisher_kind"), rs.getString("publisher_id"),
                        rs.getString("publisher_name")),
                rs.getString("ticket_id"), rs.getString("outcome"), rs.getString("approved_by"), rs.getInt("version"),
                DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)),
                DbTime.fromDb(rs.getObject("updated_at", LocalDateTime.class)),
                DbTime.fromDb(rs.getObject("completed_at", LocalDateTime.class)),
                DbTime.fromDb(rs.getObject("approved_at", LocalDateTime.class)),
                DbTime.fromDb(rs.getObject("archived_at", LocalDateTime.class)));
    }

    /** v0.0.10 🍊 The agent's current list (ACTIVE or AWAITING_APPROVAL), if any. */
    public Optional<TaskListRow> findOpen(AgentId agentId) {
        return scoped(SQL_FIND_OPEN, agentId).query(mapper).optional();
    }

    /** v0.0.10 🍊 One of the agent's lists by id (any status). */
    public Optional<TaskListRow> findById(AgentId agentId, String id) {
        return scoped(SQL_FIND_BY_ID, agentId).param("id", id).query(mapper).optional();
    }

    /** v0.0.10 🍊 The agent's latest archived lists, most recent first. */
    public List<TaskListRow> findArchived(AgentId agentId, int limit) {
        return scoped(SQL_FIND_ARCHIVED, agentId).param("limit", limit).query(mapper).list();
    }

    /** v0.0.10 🍊 Inserts a new list with a fresh {@code list-<agentHex>-<10hex>} id and returns the id. */
    public String insert(TaskListRow row) {
        return insertWithFreshId(DataName.TASK_LIST, row.agentId(), id -> scoped(SQL_INSERT, row.agentId())
                .param("id", id).param("goal", row.goal()).param("history", jsons.write(row.goalHistory()))
                .param("status", row.status().name()).param("publisherKind", row.publisher().kind().name())
                .param("publisherId", row.publisher().id()).param("publisherName", row.publisher().name())
                .param("ticketId", row.ticketId())
                .param("created", DbTime.toDb(row.createdAt())).param("updated", DbTime.toDb(row.updatedAt()))
                .update());
    }

    /** v0.0.10 🍊 Optimistic update of the mutable fields; false when the version changed meanwhile. */
    public boolean update(TaskListRow row, int expectedVersion) {
        return scoped(SQL_UPDATE, row.agentId())
                .param("goal", row.goal()).param("history", jsons.write(row.goalHistory()))
                .param("status", row.status().name()).param("ticketId", row.ticketId())
                .param("outcome", row.outcome()).param("approvedBy", row.approvedBy())
                .param("updated", DbTime.toDb(row.updatedAt())).param("completed", DbTime.toDb(row.completedAt()))
                .param("approved", DbTime.toDb(row.approvedAt())).param("archived", DbTime.toDb(row.archivedAt()))
                .param("id", row.id()).param("version", expectedVersion)
                .update() == 1;
    }

    /** v0.0.10 🍊 Parses the goal history JSON column (null means no edits yet). */
    private List<GoalChange> readHistory(String json) {
        return json == null || json.isBlank() ? List.of() : jsons.read(json, GOAL_HISTORY);
    }
}
