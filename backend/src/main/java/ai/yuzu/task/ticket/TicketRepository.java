package ai.yuzu.task.ticket;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.task.Actor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** v0.0.20 🍊 Access to the room-scoped {@code ticket} table (clustered by room_id, seq; optimistic version). */
@Repository
public class TicketRepository {

    private static final int MAX_ID_ATTEMPTS = 3;
    private static final String COLUMNS = """
            room_id, id, title, detail, status, creator_kind, creator_id, creator_name, assignee_id, assigned_by_kind,
            assigned_by_id, assigned_by_name, requester_id, requester_name, source_msg_id, list_id, version, created_at,
            updated_at""";
    private static final String SQL_FIND = "SELECT " + COLUMNS + " FROM ticket WHERE id = :id";
    private static final String SQL_LOCK = SQL_FIND + " FOR UPDATE";
    private static final String SQL_BY_ROOM = "SELECT " + COLUMNS + " FROM ticket WHERE room_id = :room"
            + " ORDER BY seq DESC LIMIT :limit";
    private static final String SQL_INSERT = """
            INSERT INTO ticket (room_id, id, title, detail, status, creator_kind, creator_id, creator_name, assignee_id,
                assigned_by_kind, assigned_by_id, assigned_by_name, requester_id, requester_name, source_msg_id,
                list_id, version, created_at, updated_at)
            VALUES (:room, :id, :title, :detail, :status, :creatorKind, :creatorId, :creatorName, :assignee, :byKind,
                :byId, :byName, :requesterId, :requesterName, :sourceMsg, :listId, 0, :created, :updated)
            """;
    private static final String SQL_UPDATE = """
            UPDATE ticket SET status = :status, assignee_id = :assignee, assigned_by_kind = :byKind,
                assigned_by_id = :byId, assigned_by_name = :byName, list_id = :listId, version = version + 1,
                updated_at = :updated
            WHERE id = :id AND version = :version
            """;

    private static final RowMapper<TicketRow> MAPPER = (rs, i) -> new TicketRow(
            rs.getString("room_id"), rs.getString("id"), rs.getString("title"), rs.getString("detail"),
            TicketStatus.valueOf(rs.getString("status")),
            Actor.fromColumns(rs.getString("creator_kind"), rs.getString("creator_id"), rs.getString("creator_name")),
            rs.getString("assignee_id"),
            Actor.fromColumns(rs.getString("assigned_by_kind"), rs.getString("assigned_by_id"),
                    rs.getString("assigned_by_name")),
            rs.getString("requester_id"), rs.getString("requester_name"), rs.getString("source_msg_id"),
            rs.getString("list_id"), rs.getInt("version"),
            DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)),
            DbTime.fromDb(rs.getObject("updated_at", LocalDateTime.class)));

    private final JdbcClient jdbc;

    /** v0.0.20 🍊 Injects the JDBC client. */
    public TicketRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** v0.0.20 🍊 Finds a ticket by id. */
    public Optional<TicketRow> findById(String id) {
        return jdbc.sql(SQL_FIND).param("id", id).query(MAPPER).optional();
    }

    /** v0.0.20 🍊 Reads the latest committed row and locks it until the surrounding transaction ends. */
    public Optional<TicketRow> lockById(String id) {
        return jdbc.sql(SQL_LOCK).param("id", id).query(MAPPER).optional();
    }

    /** v0.0.20 🍊 The latest {@code limit} tickets of a room in creation order (ascending). */
    public List<TicketRow> findByRoom(String roomId, int limit) {
        List<TicketRow> newestFirst = jdbc.sql(SQL_BY_ROOM).param("room", roomId).param("limit", limit)
                .query(MAPPER).list();
        List<TicketRow> ascending = new ArrayList<>(newestFirst);
        Collections.reverse(ascending);
        return ascending;
    }

    /** v0.0.20 🍊 Inserts a ticket with a fresh ticket-<ownerHex>-<10hex> id (retried on collision); returns it. */
    public String insert(TicketRow row, AgentId owner) {
        DuplicateKeyException last = null;
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String id = IdGen.recordId(DataName.TICKET, owner);
            try {
                jdbc.sql(SQL_INSERT)
                        .param("room", row.roomId()).param("id", id).param("title", row.title())
                        .param("detail", row.detail()).param("status", row.status().name())
                        .param("creatorKind", row.creator().kind().name()).param("creatorId", row.creator().id())
                        .param("creatorName", row.creator().name()).param("assignee", row.assigneeId())
                        .param("byKind", row.assignedBy() == null ? null : row.assignedBy().kind().name())
                        .param("byId", row.assignedBy() == null ? null : row.assignedBy().id())
                        .param("byName", row.assignedBy() == null ? null : row.assignedBy().name())
                        .param("requesterId", row.requesterId()).param("requesterName", row.requesterName())
                        .param("sourceMsg", row.sourceMessageId()).param("listId", row.listId())
                        .param("created", DbTime.toDb(row.createdAt())).param("updated", DbTime.toDb(row.updatedAt()))
                        .update();
                return id;
            } catch (DuplicateKeyException e) {
                if (!String.valueOf(e.getMessage()).contains("uk_id")) {
                    throw e;
                }
                last = e;
            }
        }
        throw last;
    }

    /** v0.0.20 🍊 Optimistic update of the mutable fields; false when the version changed meanwhile. */
    public boolean update(TicketRow row, int expectedVersion) {
        return jdbc.sql(SQL_UPDATE)
                .param("status", row.status().name()).param("assignee", row.assigneeId())
                .param("byKind", row.assignedBy() == null ? null : row.assignedBy().kind().name())
                .param("byId", row.assignedBy() == null ? null : row.assignedBy().id())
                .param("byName", row.assignedBy() == null ? null : row.assignedBy().name())
                .param("listId", row.listId()).param("updated", DbTime.toDb(row.updatedAt()))
                .param("id", row.id()).param("version", expectedVersion)
                .update() == 1;
    }
}
