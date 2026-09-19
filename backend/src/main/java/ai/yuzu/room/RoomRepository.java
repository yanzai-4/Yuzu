package ai.yuzu.room;

import ai.yuzu.common.error.NotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** v0.0.5 🍊 Access to the {@code room} table (global, not agent-scoped). */
@Repository
public class RoomRepository {

    private final JdbcClient jdbc;

    /** v0.0.5 🍊 Injects the JDBC client. */
    public RoomRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** v0.0.5 🍊 Name of an existing room; NOT_FOUND otherwise. */
    public String name(String roomId) {
        return jdbc.sql("SELECT name FROM room WHERE id = :id").param("id", roomId)
                .query(String.class).optional()
                .orElseThrow(() -> new NotFoundException("Room " + roomId + " does not exist."));
    }

    /** v0.0.5 🍊 True when the room exists. */
    public boolean exists(String roomId) {
        return jdbc.sql("SELECT COUNT(*) FROM room WHERE id = :id").param("id", roomId)
                .query(Integer.class).single() > 0;
    }
}
