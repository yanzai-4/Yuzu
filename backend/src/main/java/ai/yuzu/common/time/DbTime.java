package ai.yuzu.common.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** v0.0.1 🍊 Converts between {@link Instant} and the UTC {@code DATETIME(3)} values stored in MySQL. */
public final class DbTime {

    private DbTime() {
    }

    /** v0.0.1 🍊 Instant → UTC LocalDateTime for JDBC binding (null-safe). */
    public static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** v0.0.1 🍊 UTC LocalDateTime read from JDBC → Instant (null-safe). */
    public static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
