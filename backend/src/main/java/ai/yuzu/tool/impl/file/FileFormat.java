package ai.yuzu.tool.impl.file;

import java.util.Locale;

/** v0.0.27 🍊 English rendering helpers shared by the file tools (sizes and bounded previews). */
final class FileFormat {

    /** v0.0.27 🍊 Most characters any file tool puts into one result (larger reads continue in the next call). */
    static final int MAX_OUTPUT_CHARS = 24_000;

    private static final long KIB = 1024;
    private static final String[] UNITS = {"KB", "MB", "GB", "TB"};

    /** v0.0.27 🍊 Static helpers only. */
    private FileFormat() {
    }

    /** v0.0.27 🍊 Human-readable size such as "600 KB", "1.5 MB" or "12 bytes" (1 KB = 1024 bytes). */
    static String bytes(long size) {
        if (size < KIB) {
            return size + (size == 1 ? " byte" : " bytes");
        }
        double value = size;
        int unit = -1;
        while (value >= KIB && unit < UNITS.length - 1) {
            value /= KIB;
            unit++;
        }
        String number = String.format(Locale.US, "%.1f", value);
        return (number.endsWith(".0") ? number.substring(0, number.length() - 2) : number) + " " + UNITS[unit];
    }

    /** v0.0.27 🍊 Cuts a rendered result to the output budget with a visible marker. */
    static String cut(String text) {
        String clean = text == null ? "" : text.strip();
        return clean.length() <= MAX_OUTPUT_CHARS
                ? clean
                : clean.substring(0, MAX_OUTPUT_CHARS) + "\n(the rest was cut; read a smaller range next time)";
    }

    /** v0.0.27 🍊 Cuts the body of a result so the notes that follow it always fit in the output budget. */
    static String cutBody(String body, int reservedChars) {
        int budget = Math.max(0, MAX_OUTPUT_CHARS - reservedChars);
        return body.length() <= budget
                ? body
                : body.substring(0, budget) + "\n(the rest was cut; read a smaller range next time)\n";
    }

    /** v0.0.27 🍊 The requested path with the leading/trailing spaces removed ("" means the workspace root). */
    static String path(String raw) {
        return raw == null ? "" : raw.strip();
    }
}
