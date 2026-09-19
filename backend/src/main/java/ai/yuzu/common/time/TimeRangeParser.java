package ai.yuzu.common.time;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v0.0.19 🍊 Code-first parser of relative time phrases ("5 minutes ago", "yesterday afternoon", "past 2 hours")
 * into absolute ranges in the workgroup zone.
 *
 * <p>Code handles the common phrases for free and deterministically; only phrases it cannot resolve fall back to
 * the memory-read AI module (which also gets the current time), whose output code validates again.</p>
 */
@Component
public class TimeRangeParser {

    /** v0.0.19 🍊 A resolved, inclusive time range. */
    public record Range(Instant from, Instant to, String phrase) {
    }

    private static final String NUM = "(\\d+|an?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|"
            + "fifteen|twenty|thirty|forty-five|sixty|a few|few|a couple of|couple of|several)";
    private static final String UNIT = "(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?)";
    private static final Pattern DAY_BEFORE = Pattern.compile("\\bday before yesterday\\b");
    private static final Pattern YESTERDAY = Pattern.compile("\\byesterday(?:\\s+(morning|afternoon|evening|night))?\\b");
    private static final Pattern LAST_NIGHT = Pattern.compile("\\blast night\\b");
    private static final Pattern THIS_PART = Pattern.compile("\\b(?:this\\s+(morning|afternoon|evening)|(tonight))\\b");
    private static final Pattern TODAY = Pattern.compile("\\b(?:earlier\\s+)?today\\b");
    private static final Pattern AGO = Pattern.compile("\\b" + NUM + "\\s+" + UNIT + "\\s+ago\\b");
    private static final Pattern ROLLING = Pattern.compile(
            "\\b(?:(?:in|over|during|within|for)\\s+)?the\\s+(?:last|past|previous)\\s+(?:" + NUM + "\\s+)?" + UNIT + "\\b");
    private static final Pattern PAST = Pattern.compile("\\b(?:past|last)\\s+" + NUM + "\\s+" + UNIT + "\\b");
    private static final Pattern PAST_UNIT = Pattern.compile("\\b(?:past|last)\\s+(hour|minute|day)\\b");
    private static final Pattern CALENDAR = Pattern.compile("\\b(last|this)\\s+(week|month)\\b");
    private static final Pattern JUST_NOW = Pattern.compile("\\b(?:just now|a moment ago|moments ago|a minute ago)\\b");
    private static final Pattern RECENTLY = Pattern.compile("\\b(?:recently|lately|earlier)\\b");
    private static final Pattern CLOCK = Pattern.compile(
            "\\b(at|around|about|near)?\\s*(\\d{1,2})(?::(\\d{2}))?(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![a-z])");
    private static final Pattern CLOCK_24 = Pattern.compile("\\b(at|around|about|near)\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\b");
    private static final Pattern MENTIONS = Pattern.compile("\\b(ago|yesterday|today|tonight|tomorrow|morning|afternoon|"
            + "evening|night|noon|midnight|last|past|previous|earlier|recent|recently|lately|week|weekend|month|year|"
            + "hour|hours|minute|minutes|second|seconds|since|until|before|after|between|monday|tuesday|wednesday|"
            + "thursday|friday|saturday|sunday|january|february|march|april|june|july|august|september|october|"
            + "november|december)\\b|\\d{1,2}:\\d{2}|\\d{4}-\\d{2}-\\d{2}|\\b\\d{1,2}\\s*(am|pm)\\b");
    private static final Map<String, Integer> WORDS = Map.ofEntries(Map.entry("a", 1), Map.entry("an", 1),
            Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5),
            Map.entry("six", 6), Map.entry("seven", 7), Map.entry("eight", 8), Map.entry("nine", 9),
            Map.entry("ten", 10), Map.entry("eleven", 11), Map.entry("twelve", 12), Map.entry("fifteen", 15),
            Map.entry("twenty", 20), Map.entry("thirty", 30), Map.entry("forty-five", 45), Map.entry("sixty", 60),
            Map.entry("a few", 3), Map.entry("few", 3), Map.entry("a couple of", 2), Map.entry("couple of", 2),
            Map.entry("several", 4));

    private final NaturalTime time;

    /** v0.0.19 🍊 Binds the parser to the workgroup clock and zone. */
    public TimeRangeParser(NaturalTime time) {
        this.time = time;
    }

    /** v0.0.19 🍊 True when a text seems to talk about time (then an unparsed phrase needs the AI fallback). */
    public static boolean mentionsTime(String text) {
        return text != null && MENTIONS.matcher(text.toLowerCase(Locale.ROOT)).find();
    }

    /** v0.0.19 🍊 Resolves the first recognizable time phrase in a text against the current time. */
    public Optional<Range> parse(String text) {
        return parse(text, time.nowInstant());
    }

    /** v0.0.19 🍊 Resolves the first recognizable time phrase in a text against a given "now". */
    public Optional<Range> parse(String text, Instant nowInstant) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String t = text.toLowerCase(Locale.ROOT).replace('’', '\'');
        ZonedDateTime now = nowInstant.atZone(time.zone());
        LocalDate today = now.toLocalDate();
        Matcher m;
        if ((m = DAY_BEFORE.matcher(t)).find()) {
            return day(today.minusDays(2), now, m.group());
        }
        if ((m = YESTERDAY.matcher(t)).find()) {
            return m.group(1) == null ? day(today.minusDays(1), now, m.group())
                    : part(today.minusDays(1), m.group(1), now, m.group());
        }
        if ((m = LAST_NIGHT.matcher(t)).find()) {
            return part(today.minusDays(1), "night", now, m.group());
        }
        if ((m = THIS_PART.matcher(t)).find()) {
            return part(today, m.group(1) == null ? "evening" : m.group(1), now, m.group());
        }
        if ((m = JUST_NOW.matcher(t)).find()) {
            return range(now.minusMinutes(5), now, now, m.group());
        }
        if ((m = AGO.matcher(t)).find()) {
            return ago(number(m.group(1)), vague(m.group(1)), unit(m.group(2)), now, m.group());
        }
        if ((m = ROLLING.matcher(t)).find()) {
            int n = m.group(1) == null ? 1 : number(m.group(1));
            return range(minus(now, n, unit(m.group(2))), now, now, m.group());
        }
        if ((m = PAST.matcher(t)).find()) {
            return range(minus(now, number(m.group(1)), unit(m.group(2))), now, now, m.group());
        }
        if ((m = PAST_UNIT.matcher(t)).find()) {
            return range(minus(now, 1, unit(m.group(1))), now, now, m.group());
        }
        if ((m = CALENDAR.matcher(t)).find()) {
            return calendar(m.group(1), m.group(2), now, m.group());
        }
        if ((m = TODAY.matcher(t)).find()) {
            return range(today.atStartOfDay(time.zone()), now, now, m.group());
        }
        if ((m = CLOCK.matcher(t)).find()) {
            int hour = Integer.parseInt(m.group(2)) % 12 + (m.group(5).startsWith("p") ? 12 : 0);
            return clock(hour, m.group(3), m.group(4), m.group(1), now, m.group().strip());
        }
        if ((m = CLOCK_24.matcher(t)).find()) {
            return clock(Integer.parseInt(m.group(2)), m.group(3), m.group(4), m.group(1), now, m.group().strip());
        }
        if ((m = RECENTLY.matcher(t)).find()) {
            return range(now.minusHours(3), now, now, m.group());
        }
        return Optional.empty();
    }

    /** v0.0.19 🍊 "N units ago": a window around that moment (whole calendar days for days). */
    private Optional<Range> ago(int n, boolean vague, ChronoUnit unit, ZonedDateTime now, String phrase) {
        return switch (unit) {
            case DAYS -> day(now.toLocalDate().minusDays(n), now, phrase);
            case WEEKS -> around(now.minusWeeks(n), Duration.ofHours(84), now, phrase);
            case MONTHS -> around(now.minusMonths(n), Duration.ofDays(15), now, phrase);
            default -> {
                Duration span = unit.getDuration().multipliedBy(n);
                if (vague) {
                    yield range(now.minus(span.multipliedBy(2)), now, now, phrase);
                }
                Duration floor = unit == ChronoUnit.HOURS ? Duration.ofMinutes(30) : Duration.ofMinutes(1);
                Duration half = span.dividedBy(2).compareTo(floor) < 0 ? floor : span.dividedBy(2);
                yield around(now.minus(span), half, now, phrase);
            }
        };
    }

    /** v0.0.19 🍊 Clock time today (yesterday when still ahead of now) with a precision-dependent window. */
    private Optional<Range> clock(int hour, String minute, String second, String approx, ZonedDateTime now,
                                  String phrase) {
        if (hour > 23 || (minute != null && Integer.parseInt(minute) > 59) || (second != null && Integer.parseInt(second) > 59)) {
            return Optional.empty();
        }
        LocalTime at = LocalTime.of(hour, minute == null ? 0 : Integer.parseInt(minute),
                second == null ? 0 : Integer.parseInt(second));
        ZonedDateTime point = now.toLocalDate().atTime(at).atZone(time.zone());
        if (point.isAfter(now)) {
            point = point.minusDays(1);
        }
        boolean around = approx != null && !approx.equals("at");
        Duration half = second != null ? Duration.ofSeconds(30)
                : minute == null ? Duration.ofMinutes(30) : around ? Duration.ofMinutes(15) : Duration.ofMinutes(2);
        return around(point, half, now, phrase);
    }

    /** v0.0.19 🍊 Previous/current calendar week or month. */
    private Optional<Range> calendar(String which, String span, ZonedDateTime now, String phrase) {
        LocalDate today = now.toLocalDate();
        LocalDate start = span.equals("week") ? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : today.withDayOfMonth(1);
        if (which.equals("this")) {
            return range(start.atStartOfDay(time.zone()), now, now, phrase);
        }
        LocalDate previous = span.equals("week") ? start.minusWeeks(1) : start.minusMonths(1);
        return range(previous.atStartOfDay(time.zone()), start.atStartOfDay(time.zone()).minusNanos(1_000_000), now,
                phrase);
    }

    /** v0.0.19 🍊 A part of a day (morning 0–12, afternoon 12–18, evening 18–24, night 18–6 next day). */
    private Optional<Range> part(LocalDate day, String part, ZonedDateTime now, String phrase) {
        ZonedDateTime start = day.atStartOfDay(time.zone());
        return switch (part) {
            case "morning" -> range(start, start.plusHours(12), now, phrase);
            case "afternoon" -> range(start.plusHours(12), start.plusHours(18), now, phrase);
            case "night" -> range(start.plusHours(18), start.plusHours(30), now, phrase);
            default -> range(start.plusHours(18), start.plusHours(24), now, phrase);
        };
    }

    /** v0.0.19 🍊 A whole calendar day. */
    private Optional<Range> day(LocalDate day, ZonedDateTime now, String phrase) {
        ZonedDateTime start = day.atStartOfDay(time.zone());
        return range(start, start.plusDays(1).minusNanos(1_000_000), now, phrase);
    }

    /** v0.0.19 🍊 A window of ±half around a point. */
    private Optional<Range> around(ZonedDateTime point, Duration half, ZonedDateTime now, String phrase) {
        return range(point.minus(half), point.plus(half), now, phrase);
    }

    /** v0.0.19 🍊 Builds the range, clipping its end to now (a range entirely in the future becomes empty-width). */
    private Optional<Range> range(ZonedDateTime from, ZonedDateTime to, ZonedDateTime now, String phrase) {
        Instant end = to.isAfter(now) ? now.toInstant() : to.toInstant();
        Instant start = from.toInstant();
        return Optional.of(new Range(start, end.isBefore(start) ? start : end, phrase.strip()));
    }

    /** v0.0.19 🍊 now minus n units (calendar-aware for months). */
    private static ZonedDateTime minus(ZonedDateTime now, int n, ChronoUnit unit) {
        return now.minus(n, unit);
    }

    /** v0.0.19 🍊 Number words and digits to an int (at least 1). */
    private static int number(String word) {
        String w = word.strip();
        Integer known = WORDS.get(w);
        if (known != null) {
            return known;
        }
        try {
            return Math.max(1, Math.min(Integer.parseInt(w), 10_000));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** v0.0.19 🍊 Vague quantities ("a few", "several") widen the window to everything since. */
    private static boolean vague(String word) {
        return word.contains("few") || word.contains("couple") || word.contains("several");
    }

    /** v0.0.19 🍊 Unit word to a chrono unit. */
    private static ChronoUnit unit(String word) {
        String w = word.strip();
        if (w.startsWith("sec")) {
            return ChronoUnit.SECONDS;
        }
        if (w.startsWith("min")) {
            return ChronoUnit.MINUTES;
        }
        if (w.startsWith("h")) {
            return ChronoUnit.HOURS;
        }
        if (w.startsWith("d")) {
            return ChronoUnit.DAYS;
        }
        if (w.startsWith("w")) {
            return ChronoUnit.WEEKS;
        }
        return ChronoUnit.MONTHS;
    }
}
