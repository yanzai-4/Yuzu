package ai.yuzu.llm.structured;

/**
 * v0.0.10 🍊 Tolerant extraction of the first complete JSON object from model text.
 *
 * <p>Strips Markdown code fences and leading prose, then scans for the first balanced {...} while
 * respecting string literals and escapes. Used for every strategy (strict answers pass through unchanged).</p>
 */
public final class JsonExtractor {

    private JsonExtractor() {
    }

    /** v0.0.10 🍊 The first balanced JSON object, or null when there is none. */
    public static String extract(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        int start = trimmed.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return trimmed.substring(start, i + 1);
                }
            }
        }
        return null;
    }
}
