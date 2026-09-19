package ai.yuzu.internal.memory;

import java.util.List;
import java.util.Locale;

/**
 * v0.0.28 🍊 One entry offered to a long-term memory, before any de-duplication or judgement.
 *
 * @param kind     which memory it belongs to
 * @param title    habit name / deep-memory title
 * @param scenario HABIT only: when the habit applies ("" for deep memories)
 * @param body     habit technique / deep-memory content
 * @param keywords DEEP only: extra search terms (empty for habits)
 * @param source   DEEP only: SUBCONSCIOUS, MANUAL or SYSTEM
 */
public record MemoryCandidate(MemoryKind kind, String title, String scenario, String body, List<String> keywords,
                              String source) {

    /** v0.0.28 🍊 Longest title accepted (the column is VARCHAR(200)). */
    public static final int MAX_TITLE = 200;
    /** v0.0.28 🍊 Longest keyword line accepted (the column is VARCHAR(500)). */
    public static final int MAX_KEYWORDS = 500;
    /** v0.0.28 🍊 Longest body kept inline. */
    public static final int MAX_BODY = 20_000;

    /** v0.0.28 🍊 A habit candidate (scenario + technique). */
    public static MemoryCandidate habit(String name, String scenario, String technique) {
        return new MemoryCandidate(MemoryKind.HABIT, clean(name, MAX_TITLE), clean(scenario, MAX_BODY),
                clean(technique, MAX_BODY), List.of(), "SUBCONSCIOUS");
    }

    /** v0.0.28 🍊 A deep-memory candidate (title + content + keywords). */
    public static MemoryCandidate deep(String title, String content, List<String> keywords, String source) {
        return new MemoryCandidate(MemoryKind.DEEP, clean(title, MAX_TITLE), "", clean(content, MAX_BODY),
                keywords == null ? List.of() : keywords.stream().filter(k -> k != null && !k.isBlank())
                        .map(String::strip).distinct().limit(8).toList(),
                source == null || source.isBlank() ? "SUBCONSCIOUS" : source);
    }

    /** v0.0.28 🍊 True when the candidate has enough substance to be stored at all. */
    public boolean isUsable() {
        return !title.isBlank() && !body.isBlank();
    }

    /** v0.0.28 🍊 The keyword column value (comma separated, bounded). */
    public String keywordLine() {
        String line = String.join(", ", keywords);
        return line.length() > MAX_KEYWORDS ? line.substring(0, MAX_KEYWORDS) : line;
    }

    /** v0.0.28 🍊 The FULLTEXT query used to look for similar entries. */
    public String searchText() {
        String text = kind == MemoryKind.HABIT ? title + " " + scenario : title + " " + keywordLine();
        return text.replaceAll("[\"+\\-<>()~*@]", " ").replaceAll("\\s+", " ").strip();
    }

    /** v0.0.28 🍊 What the judge and the pool see. */
    public String describe() {
        return kind == MemoryKind.HABIT
                ? title + " — when: " + scenario + " — technique: " + body
                : title + " — " + body + (keywords.isEmpty() ? "" : " [" + keywordLine() + "]");
    }

    /** v0.0.28 🍊 Normalized text the content hash is taken over (case and whitespace insensitive). */
    public String hashSource() {
        return kind.name() + "\n" + normalize(title) + "\n" + normalize(scenario) + "\n" + normalize(body);
    }

    /** v0.0.28 🍊 Lowercase, whitespace-collapsed copy. */
    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    /** v0.0.28 🍊 Trimmed, length-bounded field value (null becomes ""). */
    private static String clean(String value, int max) {
        String text = value == null ? "" : value.strip();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
