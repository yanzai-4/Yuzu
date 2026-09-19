package ai.yuzu.common.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.19 🍊 Relative time phrases resolve to the expected absolute windows (workgroup zone America/Los_Angeles). */
class TimeRangeParserTest {

    /** Saturday, September 19, 2026 at 1:10:00 PM PDT. */
    private final Instant now = Instant.parse("2026-09-19T20:10:00Z");
    private final NaturalTime time = new NaturalTime(Clock.fixed(now, ZoneOffset.UTC), ZoneId.of("America/Los_Angeles"));
    private final TimeRangeParser parser = new TimeRangeParser(time);

    /** v0.0.19 🍊 "5 minutes ago" is a window around that moment, clipped to now. */
    @Test
    void minutesAgo() {
        TimeRangeParser.Range r = parser.parse("what did Alice ask 5 minutes ago?").orElseThrow();
        assertThat(time.machine(r.from())).isEqualTo("2026-09-19 13:02:30");
        assertThat(time.machine(r.to())).isEqualTo("2026-09-19 13:07:30");
        assertThat(r.phrase()).isEqualTo("5 minutes ago");
    }

    /** v0.0.19 🍊 Vague quantities cover everything since. */
    @Test
    void aFewMinutesAgo() {
        TimeRangeParser.Range r = parser.parse("a few minutes ago").orElseThrow();
        assertThat(time.machine(r.from())).isEqualTo("2026-09-19 13:04:00");
        assertThat(r.to()).isEqualTo(now);
    }

    /** v0.0.19 🍊 Days are calendar days; parts of days are fixed windows. */
    @Test
    void calendarDaysAndParts() {
        TimeRangeParser.Range y = parser.parse("Yesterday").orElseThrow();
        assertThat(time.machine(y.from())).isEqualTo("2026-09-18 00:00:00");
        assertThat(time.machine(y.to())).isEqualTo("2026-09-18 23:59:59");
        TimeRangeParser.Range ya = parser.parse("yesterday afternoon").orElseThrow();
        assertThat(time.machine(ya.from())).isEqualTo("2026-09-18 12:00:00");
        assertThat(time.machine(ya.to())).isEqualTo("2026-09-18 18:00:00");
        TimeRangeParser.Range tm = parser.parse("this morning").orElseThrow();
        assertThat(time.machine(tm.from())).isEqualTo("2026-09-19 00:00:00");
        assertThat(time.machine(tm.to())).isEqualTo("2026-09-19 12:00:00");
        TimeRangeParser.Range two = parser.parse("2 days ago").orElseThrow();
        assertThat(time.machine(two.from())).isEqualTo("2026-09-17 00:00:00");
    }

    /** v0.0.19 🍊 Rolling and calendar spans. */
    @Test
    void rollingAndCalendarSpans() {
        TimeRangeParser.Range past = parser.parse("in the past 2 hours").orElseThrow();
        assertThat(time.machine(past.from())).isEqualTo("2026-09-19 11:10:00");
        assertThat(past.to()).isEqualTo(now);
        TimeRangeParser.Range lastWeek = parser.parse("last week").orElseThrow();
        assertThat(time.machine(lastWeek.from())).isEqualTo("2026-09-07 00:00:00");
        assertThat(time.machine(lastWeek.to())).isEqualTo("2026-09-13 23:59:59");
        TimeRangeParser.Range thisWeek = parser.parse("this week").orElseThrow();
        assertThat(time.machine(thisWeek.from())).isEqualTo("2026-09-14 00:00:00");
    }

    /** v0.0.19 🍊 Clock times resolve to today (or yesterday when still ahead). */
    @Test
    void clockTimes() {
        TimeRangeParser.Range r = parser.parse("around 11:30 am").orElseThrow();
        assertThat(time.machine(r.from())).isEqualTo("2026-09-19 11:15:00");
        assertThat(time.machine(r.to())).isEqualTo("2026-09-19 11:45:00");
        TimeRangeParser.Range later = parser.parse("at 3 pm").orElseThrow();
        assertThat(time.machine(later.from())).isEqualTo("2026-09-18 14:30:00");
    }

    /** v0.0.19 🍊 Unknown phrases stay unresolved (the AI fallback handles them); time talk is detected. */
    @Test
    void unresolvedPhrases() {
        assertThat(parser.parse("the morning of the launch")).isEmpty();
        assertThat(parser.parse("the Citrus Spark brief")).isEmpty();
        assertThat(TimeRangeParser.mentionsTime("the morning of the launch")).isTrue();
        assertThat(TimeRangeParser.mentionsTime("the Citrus Spark brief")).isFalse();
    }
}
