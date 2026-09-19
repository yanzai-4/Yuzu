package ai.yuzu.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * v0.0.1 🍊 Renders and parses the natural-language times shown to agents, humans and logs.
 *
 * <p>Yuzu never shows epoch numbers: every time is a human sentence precise to the second, in the
 * workgroup time zone. The database stores {@code DATETIME(3)} in UTC; this class is the only place
 * that converts between the two worlds.</p>
 */
public final class NaturalTime {

    private static final DateTimeFormatter FULL =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm:ss a zzz", Locale.US);
    private static final DateTimeFormatter COMPACT =
            DateTimeFormatter.ofPattern("EEE MMM d, h:mm:ss a", Locale.US);
    private static final DateTimeFormatter MACHINE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private final Clock clock;
    private final ZoneId zone;

    /** v0.0.1 🍊 Creates a renderer bound to a clock and the workgroup zone. */
    public NaturalTime(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    /** v0.0.1 🍊 The current instant, truncated to milliseconds (the DB precision). */
    public Instant nowInstant() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /** v0.0.1 🍊 "Saturday, September 19, 2026 at 11:32:05 AM PDT" for the current moment. */
    public String now() {
        return full(nowInstant());
    }

    /** v0.0.1 🍊 Full natural-language rendering of an instant. */
    public String full(Instant instant) {
        return FULL.format(instant.atZone(zone));
    }

    /** v0.0.1 🍊 Compact rendering "Sat Sep 19, 11:32:05 AM" used inside long lists (chat windows, logs). */
    public String compact(Instant instant) {
        return COMPACT.format(instant.atZone(zone));
    }

    /** v0.0.1 🍊 Canonical local "yyyy-MM-dd HH:mm:ss" form that AI outputs use for time ranges. */
    public String machine(Instant instant) {
        return MACHINE.format(instant.atZone(zone));
    }

    /** v0.0.1 🍊 Parses the canonical local "yyyy-MM-dd HH:mm:ss" form; empty when malformed. */
    public Optional<Instant> parseMachine(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(text.trim(), MACHINE).atZone(zone).toInstant());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** v0.0.1 🍊 The workgroup time zone. */
    public ZoneId zone() {
        return zone;
    }

    /** v0.0.1 🍊 The underlying clock (tests inject a fixed one). */
    public Clock clock() {
        return clock;
    }
}
