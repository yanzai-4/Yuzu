package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/** v0.0.12 🍊 Generates and sanitizes trace and span ids (ASCII, at most 32 characters, the module_event column width). */
public final class TraceIds {

    /** v0.0.12 🍊 Maximum length of a trace or span id (VARCHAR(32) columns). */
    public static final int MAX_LENGTH = 32;

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._:-]{1,32}$");
    private static final Pattern INVALID_CHARS = Pattern.compile("[^A-Za-z0-9._:-]");
    private static final HexFormat HEX = HexFormat.of();

    /** v0.0.12 🍊 Static helpers only. */
    private TraceIds() {
    }

    /** v0.0.12 🍊 New trace id {@code trace-<agentHex>-<10hex>} naming the agent (0000 for humans) that started it. */
    public static String newTraceId(AgentId origin) {
        return "trace-" + hexOf(origin) + "-" + random10();
    }

    /** v0.0.12 🍊 New span id {@code span-<agentHex>-<10hex>}. */
    public static String newSpanId(AgentId owner) {
        return "span-" + hexOf(owner) + "-" + random10();
    }

    /** v0.0.12 🍊 True when the id is 1-32 letters, digits, '.', '_', ':' or '-'. */
    public static boolean isValid(String id) {
        return id != null && VALID.matcher(id).matches();
    }

    /** v0.0.12 🍊 Null for blank ids, the id itself when valid, otherwise a deterministic sanitized copy that fits the column. */
    public static String clean(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        if (isValid(id)) {
            return id;
        }
        String safe = INVALID_CHARS.matcher(id.strip()).replaceAll("_");
        return safe.length() > MAX_LENGTH ? safe.substring(0, MAX_LENGTH) : safe;
    }

    /** v0.0.12 🍊 The cleaned trace id, or a new trace started by the agent when none was given. */
    public static String cleanOrNew(String traceId, AgentId origin) {
        String cleaned = clean(traceId);
        return cleaned != null ? cleaned : newTraceId(origin);
    }

    /** v0.0.12 🍊 The 4-hex owner part (0000 when the owner is unknown). */
    private static String hexOf(AgentId agentId) {
        return (agentId == null ? AgentId.SYSTEM : agentId).hex();
    }

    /** v0.0.12 🍊 Ten random lowercase hex digits (40 bits). */
    private static String random10() {
        return HEX.toHexDigits(ThreadLocalRandom.current().nextLong()).substring(6);
    }
}
