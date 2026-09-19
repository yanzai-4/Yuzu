package ai.yuzu.settings;

import ai.yuzu.common.time.DbTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/** v0.0.7 🍊 Key/value JSON store backed by the {@code app_setting} table (settings, keys, learned capabilities). */
@Repository
public class AppSettingRepository {

    private final JdbcClient jdbc;

    /** v0.0.7 🍊 Injects the JDBC client. */
    public AppSettingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** v0.0.7 🍊 Reads the JSON value of a key. */
    public Optional<String> get(String key) {
        return jdbc.sql("SELECT v FROM app_setting WHERE k = :k").param("k", key).query(String.class).optional();
    }

    /** v0.0.7 🍊 Inserts or replaces the JSON value of a key (version is bumped on every write). */
    public void put(String key, String json, Instant now) {
        jdbc.sql("""
                        INSERT INTO app_setting (k, v, version, updated_at) VALUES (:k, :v, 0, :ts)
                        ON DUPLICATE KEY UPDATE v = VALUES(v), version = version + 1, updated_at = VALUES(updated_at)
                        """)
                .param("k", key).param("v", json).param("ts", DbTime.toDb(now)).update();
    }
}
