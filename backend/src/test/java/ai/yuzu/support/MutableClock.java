package ai.yuzu.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** v0.0.11 🍊 Test clock that only moves when told to (time windows such as dedupe and hourly limits). */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    /** v0.0.11 🍊 Starts at an instant in UTC. */
    public MutableClock(Instant start) {
        this(start, ZoneOffset.UTC);
    }

    /** v0.0.11 🍊 Starts at an instant in a zone. */
    public MutableClock(Instant start, ZoneId zone) {
        this.now = new AtomicReference<>(start);
        this.zone = zone;
    }

    /** v0.0.11 🍊 Moves the clock forward (or backward for negative durations). */
    public void advance(Duration duration) {
        now.updateAndGet(instant -> instant.plus(duration));
    }

    /** v0.0.11 🍊 Jumps to an instant. */
    public void set(Instant instant) {
        now.set(instant);
    }

    /** v0.0.11 🍊 The clock's zone. */
    @Override
    public ZoneId getZone() {
        return zone;
    }

    /** v0.0.11 🍊 A detached copy at the same instant in another zone. */
    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(now.get(), newZone);
    }

    /** v0.0.11 🍊 The current (test-controlled) instant. */
    @Override
    public Instant instant() {
        return now.get();
    }
}
