package ai.yuzu.workspace;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** v0.0.11 🍊 Naming helpers: safe slugs, random suffixes, temporary-file names and human-readable sizes. */
final class FileNames {

    /** v0.0.11 🍊 Prefix of in-flight temporary files (hidden from listings). */
    static final String TEMP_PREFIX = ".yuzu-tmp-";

    private static final int MAX_SLUG_CHARS = 48;
    private static final long KIB = 1024;

    /** v0.0.11 🍊 Static helpers only. */
    private FileNames() {
    }

    /** v0.0.11 🍊 Lowercase slug made of [a-z0-9._-]; returns the fallback when nothing usable is left. */
    static String slug(String label, String fallback) {
        String lower = label == null ? "" : label.toLowerCase(Locale.ROOT);
        StringBuilder slug = new StringBuilder();
        for (int i = 0; i < lower.length() && slug.length() < MAX_SLUG_CHARS; i++) {
            char c = lower.charAt(i);
            boolean safe = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            if (safe) {
                slug.append(c);
            } else if (!slug.isEmpty() && slug.charAt(slug.length() - 1) != '-') {
                slug.append('-');
            }
        }
        String trimmed = trimEdges(slug.toString());
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    /** v0.0.11 🍊 Six random lowercase hex digits that keep generated names unique. */
    static String randomSuffix() {
        return String.format(Locale.ROOT, "%06x", ThreadLocalRandom.current().nextInt(1 << 24));
    }

    /** v0.0.11 🍊 A fresh temporary file name in the reserved hidden namespace. */
    static String tempName() {
        return TEMP_PREFIX + Long.toHexString(ThreadLocalRandom.current().nextLong()) + ".tmp";
    }

    /** v0.0.11 🍊 True for in-flight temporary files created by atomic writes. */
    static boolean isTemp(String name) {
        return name.startsWith(TEMP_PREFIX);
    }

    /** v0.0.11 🍊 Human-readable size such as "600 KB", "1.5 MB" or "12 bytes" (1 KB = 1024 bytes). */
    static String humanBytes(long bytes) {
        if (bytes < KIB) {
            return bytes + (bytes == 1 ? " byte" : " bytes");
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= KIB && unit < units.length - 1) {
            value /= KIB;
            unit++;
        }
        String number = String.format(Locale.US, "%.1f", value);
        if (number.endsWith(".0")) {
            number = number.substring(0, number.length() - 2);
        }
        return number + " " + units[unit];
    }

    /** v0.0.11 🍊 Removes leading and trailing dots and dashes so a slug never looks hidden or relative. */
    private static String trimEdges(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && (value.charAt(start) == '.' || value.charAt(start) == '-')) {
            start++;
        }
        while (end > start && (value.charAt(end - 1) == '.' || value.charAt(end - 1) == '-')) {
            end--;
        }
        return value.substring(start, end);
    }
}
