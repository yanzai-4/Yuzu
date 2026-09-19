package ai.yuzu.support;

import ai.yuzu.common.id.IdGen;
import org.springframework.jdbc.core.simple.JdbcClient;

/** v0.0.11 🍊 Creates throw-away rooms so integration tests never share the 8-agent limit of room-0001. */
public final class TestRooms {

    /** v0.0.11 🍊 Static helpers only. */
    private TestRooms() {
    }

    /** v0.0.11 🍊 Inserts a new room with a random free id and returns the id. */
    public static String create(JdbcClient jdbc) {
        for (int attempt = 0; attempt < 50; attempt++) {
            String id = IdGen.newRoomId();
            int inserted = jdbc.sql("INSERT IGNORE INTO room (id, name, created_at) VALUES (:id, :name, UTC_TIMESTAMP(3))")
                    .param("id", id).param("name", "Test room " + id).update();
            if (inserted == 1) {
                return id;
            }
        }
        throw new IllegalStateException("Could not allocate a free test room id.");
    }
}
