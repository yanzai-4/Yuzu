package ai.yuzu.monitor;

import com.fasterxml.jackson.databind.JsonNode;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** v0.0.12 🍊 Turns arbitrary detail values into bounded, JSON-safe structures so persisting and publishing never fail. */
final class DetailSanitizer {

    /** v0.0.12 🍊 Maximum entries kept per map or list. */
    static final int MAX_ENTRIES = 32;
    /** v0.0.12 🍊 Maximum nesting depth; deeper structures become a placeholder. */
    static final int MAX_DEPTH = 4;
    /** v0.0.12 🍊 Maximum length of one string value. */
    static final int MAX_STRING = 1_000;
    /** v0.0.12 🍊 Total characters of string content kept per detail map. */
    static final int BUDGET = 16_000;

    private static final int MAX_KEY = 64;
    private static final String MORE = "…";

    /** v0.0.12 🍊 Static helpers only. */
    private DetailSanitizer() {
    }

    /** v0.0.12 🍊 Sanitized copy of a detail map (null when empty; a placeholder when it cannot be captured). */
    static Map<String, Object> sanitize(Map<String, ?> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return map(detail, 0, new int[]{BUDGET});
        } catch (Throwable e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("detailError", e.getClass().getSimpleName());
            return fallback;
        }
    }

    /** v0.0.12 🍊 Sanitized copy of one detail value (a placeholder string when it cannot be captured). */
    static Object value(Object value) {
        try {
            return convert(value, 0, new int[]{BUDGET});
        } catch (Throwable e) {
            return "<" + e.getClass().getSimpleName() + ">";
        }
    }

    /** v0.0.12 🍊 Converts one value: scalars stay, containers are copied with limits, everything else becomes text. */
    private static Object convert(Object value, int depth, int[] budget) {
        if (value == null || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Double d) {
            return Double.isFinite(d) ? d : d.toString();
        }
        if (value instanceof Float f) {
            return Float.isFinite(f) ? f : f.toString();
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte
                || value instanceof BigDecimal || value instanceof BigInteger) {
            return value;
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof CharSequence || value instanceof Character || value instanceof Number) {
            return text(value.toString(), budget);
        }
        if (value instanceof Optional<?> optional) {
            return convert(optional.orElse(null), depth, budget);
        }
        if (value instanceof JsonNode node) {
            return json(node, depth, budget);
        }
        if (value instanceof Map<?, ?> map) {
            return depth >= MAX_DEPTH ? "{" + MORE + "}" : map(map, depth + 1, budget);
        }
        if (value instanceof Iterable<?> iterable) {
            return depth >= MAX_DEPTH ? "[" + MORE + "]" : list(iterable.iterator(), depth + 1, budget);
        }
        if (value.getClass().isArray()) {
            return depth >= MAX_DEPTH ? "[" + MORE + "]" : array(value, depth + 1, budget);
        }
        return text(safeToString(value), budget);
    }

    /** v0.0.12 🍊 Copies a map with string keys, at most MAX_ENTRIES entries. */
    private static Map<String, Object> map(Map<?, ?> source, int depth, int[] budget) {
        Map<String, Object> copy = new LinkedHashMap<>();
        int skipped = 0;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (copy.size() >= MAX_ENTRIES) {
                skipped++;
                continue;
            }
            copy.put(TextClip.truncate(String.valueOf(entry.getKey()), MAX_KEY), convert(entry.getValue(), depth, budget));
        }
        if (skipped > 0) {
            copy.put("moreEntries", skipped);
        }
        return copy;
    }

    /** v0.0.12 🍊 Copies at most MAX_ENTRIES elements of an iteration. */
    private static List<Object> list(Iterator<?> source, int depth, int[] budget) {
        List<Object> copy = new ArrayList<>();
        while (source.hasNext()) {
            if (copy.size() >= MAX_ENTRIES) {
                copy.add(MORE);
                break;
            }
            copy.add(convert(source.next(), depth, budget));
        }
        return copy;
    }

    /** v0.0.12 🍊 Copies at most MAX_ENTRIES elements of any (also primitive) array. */
    private static List<Object> array(Object source, int depth, int[] budget) {
        int length = Array.getLength(source);
        List<Object> copy = new ArrayList<>();
        for (int i = 0; i < Math.min(length, MAX_ENTRIES); i++) {
            copy.add(convert(Array.get(source, i), depth, budget));
        }
        if (length > MAX_ENTRIES) {
            copy.add(MORE);
        }
        return copy;
    }

    /** v0.0.12 🍊 Converts a Jackson tree into plain maps, lists and scalars. */
    private static Object json(JsonNode node, int depth, int[] budget) {
        if (node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return convert(node.numberValue(), depth, budget);
        }
        if (node.isTextual()) {
            return text(node.textValue(), budget);
        }
        if (!node.isContainerNode()) {
            return text(node.asText(), budget);
        }
        if (depth >= MAX_DEPTH) {
            return node.isArray() ? "[" + MORE + "]" : "{" + MORE + "}";
        }
        if (node.isArray()) {
            List<Object> copy = new ArrayList<>();
            for (JsonNode child : node) {
                if (copy.size() >= MAX_ENTRIES) {
                    copy.add(MORE);
                    break;
                }
                copy.add(json(child, depth + 1, budget));
            }
            return copy;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (copy.size() >= MAX_ENTRIES) {
                copy.put("moreEntries", node.size() - MAX_ENTRIES);
                break;
            }
            copy.put(TextClip.truncate(field.getKey(), MAX_KEY), json(field.getValue(), depth + 1, budget));
        }
        return copy;
    }

    /** v0.0.12 🍊 Clips a string to the per-value limit and the remaining budget of the whole detail map. */
    private static String text(String value, int[] budget) {
        if (budget[0] <= 0) {
            return MORE;
        }
        String clipped = TextClip.truncate(value, Math.min(MAX_STRING, budget[0]));
        budget[0] -= clipped.length();
        return clipped;
    }

    /** v0.0.12 🍊 toString that survives broken or recursive implementations. */
    private static String safeToString(Object value) {
        try {
            return String.valueOf(value);
        } catch (Throwable e) {
            return "<" + value.getClass().getSimpleName() + ">";
        }
    }
}
