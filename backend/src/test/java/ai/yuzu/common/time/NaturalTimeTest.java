package ai.yuzu.common.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.1 🍊 Verifies the natural-language time formats and the machine round-trip. */
class NaturalTimeTest {

    private final Instant instant = Instant.parse("2026-09-19T18:32:05.123Z");
    private final NaturalTime time = new NaturalTime(Clock.fixed(instant, ZoneOffset.UTC),
            ZoneId.of("America/Los_Angeles"));

    /** v0.0.1 🍊 The full form is a sentence precise to the second, in the workgroup zone. */
    @Test
    void fullFormIsNaturalLanguage() {
        assertThat(time.now()).isEqualTo("Saturday, September 19, 2026 at 11:32:05 AM PDT");
    }

    /** v0.0.1 🍊 The compact form is used in long lists. */
    @Test
    void compactForm() {
        assertThat(time.compact(instant)).isEqualTo("Sat Sep 19, 11:32:05 AM");
    }

    /** v0.0.1 🍊 The machine form round-trips (second precision) and rejects garbage. */
    @Test
    void machineRoundTrip() {
        String machine = time.machine(instant);
        assertThat(machine).isEqualTo("2026-09-19 11:32:05");
        assertThat(time.parseMachine(machine)).contains(Instant.parse("2026-09-19T18:32:05Z"));
        assertThat(time.parseMachine("yesterday")).isEmpty();
    }

    /** v0.0.1 🍊 DB conversion is UTC and lossless at millisecond precision. */
    @Test
    void dbTimeRoundTrip() {
        assertThat(DbTime.fromDb(DbTime.toDb(instant))).isEqualTo(instant);
    }
}
