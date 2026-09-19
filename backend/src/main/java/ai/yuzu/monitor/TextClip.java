package ai.yuzu.monitor;

import java.util.regex.Pattern;

/** v0.0.12 🍊 Safe text shortening: never splits a surrogate pair and marks every cut with an ellipsis. */
final class TextClip {

    /** v0.0.12 🍊 Marker appended to shortened text. */
    static final String ELLIPSIS = "…";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String TRAILING_JUNK = ",;:-–— ";

    /** v0.0.12 🍊 Static helpers only. */
    private TextClip() {
    }

    /** v0.0.12 🍊 Cuts the text to at most {@code max} characters (ending with an ellipsis when cut), keeping line breaks. */
    static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, safeCut(text, Math.max(0, max - 1))) + ELLIPSIS;
    }

    /** v0.0.12 🍊 Collapses whitespace and cuts at a word boundary to at most {@code max} characters. */
    static String oneLine(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = WHITESPACE.matcher(text).replaceAll(" ").strip();
        if (flat.length() <= max) {
            return flat;
        }
        int limit = safeCut(flat, Math.max(0, max - 1));
        int space = flat.lastIndexOf(' ', limit);
        int cut = space >= max / 2 ? space : limit;
        return stripTrailingJunk(flat.substring(0, cut)) + ELLIPSIS;
    }

    /** v0.0.12 🍊 Moves a cut index back by one when it would split a surrogate pair. */
    private static int safeCut(String text, int index) {
        if (index <= 0) {
            return 0;
        }
        return Character.isHighSurrogate(text.charAt(index - 1)) ? index - 1 : index;
    }

    /** v0.0.12 🍊 Drops separators left dangling before the ellipsis. */
    private static String stripTrailingJunk(String text) {
        int end = text.length();
        while (end > 0 && TRAILING_JUNK.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(0, end);
    }
}
