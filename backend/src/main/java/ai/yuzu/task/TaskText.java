package ai.yuzu.task;

import ai.yuzu.common.error.BadRequestException;

import java.util.regex.Pattern;

/** v0.0.10 🍊 Normalizes and bounds the free text of tickets and task lists (one-line texts keep prompts stable). */
public final class TaskText {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** v0.0.10 🍊 Static helpers only. */
    private TaskText() {
    }

    /** v0.0.10 🍊 Collapses every whitespace run (newlines included) into one space and strips; null becomes "". */
    public static String oneLine(String raw) {
        return raw == null ? "" : WHITESPACE.matcher(raw).replaceAll(" ").strip();
    }

    /** v0.0.10 🍊 Required one-line text; BAD_REQUEST when blank or longer than {@code max}. */
    public static String required(String raw, String field, int max) {
        String text = oneLine(raw);
        if (text.isEmpty()) {
            throw new BadRequestException(field + " is required.").with("field", field);
        }
        return bounded(text, field, max);
    }

    /** v0.0.10 🍊 Optional one-line text; null when blank, BAD_REQUEST when longer than {@code max}. */
    public static String optional(String raw, String field, int max) {
        String text = oneLine(raw);
        return text.isEmpty() ? null : bounded(text, field, max);
    }

    /** v0.0.10 🍊 Multi-line text with normalized line breaks, stripped; "" when null, BAD_REQUEST when too long. */
    public static String multiLine(String raw, String field, int max) {
        String text = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n').strip();
        return bounded(text, field, max);
    }

    /** v0.0.10 🍊 Enforces the maximum length of a field. */
    private static String bounded(String text, String field, int max) {
        if (text.length() > max) {
            throw new BadRequestException(field + " can have at most " + max + " characters.")
                    .with("field", field).with("max", max);
        }
        return text;
    }
}
