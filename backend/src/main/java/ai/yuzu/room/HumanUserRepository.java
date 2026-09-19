package ai.yuzu.room;

import ai.yuzu.common.time.DbTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** v0.0.5 🍊 Access to the {@code human_user} table. */
@Repository
public class HumanUserRepository {

    private static final RowMapper<UserView> MAPPER = (rs, i) -> new UserView(
            rs.getString("id"), rs.getString("username"), rs.getString("color"), rs.getString("room_id"));

    private final JdbcClient jdbc;

    /** v0.0.5 🍊 Injects the JDBC client. */
    public HumanUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** v0.0.5 🍊 Finds a user of a room by username (case-insensitive collation). */
    public Optional<UserView> findByUsername(String roomId, String username) {
        return jdbc.sql("SELECT id, username, color, room_id FROM human_user WHERE room_id = :room AND username = :name")
                .param("room", roomId).param("name", username).query(MAPPER).optional();
    }

    /** v0.0.5 🍊 Finds a user by id. */
    public Optional<UserView> findById(String userId) {
        return jdbc.sql("SELECT id, username, color, room_id FROM human_user WHERE id = :id")
                .param("id", userId).query(MAPPER).optional();
    }

    /** v0.0.5 🍊 All users of a room in join order. */
    public List<UserView> findByRoom(String roomId) {
        return jdbc.sql("SELECT id, username, color, room_id FROM human_user WHERE room_id = :room ORDER BY created_at")
                .param("room", roomId).query(MAPPER).list();
    }

    /** v0.0.5 🍊 Inserts a new user. */
    public void insert(UserView user, Instant now) {
        LocalDateTime ts = DbTime.toDb(now);
        jdbc.sql("""
                        INSERT INTO human_user (id, room_id, username, color, created_at, last_seen_at)
                        VALUES (:id, :room, :name, :color, :ts, :ts)
                        """)
                .param("id", user.id()).param("room", user.roomId()).param("name", user.username())
                .param("color", user.color()).param("ts", ts).update();
    }

    /** v0.0.5 🍊 Updates the last-seen time on re-join. */
    public void touch(String userId, Instant now) {
        jdbc.sql("UPDATE human_user SET last_seen_at = :ts WHERE id = :id")
                .param("ts", DbTime.toDb(now)).param("id", userId).update();
    }
}
