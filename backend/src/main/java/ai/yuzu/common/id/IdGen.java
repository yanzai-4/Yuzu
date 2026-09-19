package ai.yuzu.common.id;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * v0.0.1 🍊 Generates every identifier used by Yuzu.
 *
 * <ul>
 *   <li>agents {@code agent-xxxx}, humans {@code user-xxxx}, rooms {@code room-xxxx} (4 hex, never 0000);</li>
 *   <li>records {@code <dataName>-<agentHex>-<10hex>} (40 random bits; uniqueness enforced by the DB).</li>
 * </ul>
 * Callers must retry on a unique-key collision; the random space makes that extremely rare.
 */
public final class IdGen {

    private static final SecureRandom SECURE = new SecureRandom();
    private static final long RECORD_SPACE = 1L << 40;
    private static final Pattern RECORD_FORMAT = Pattern.compile("^[a-z][a-z0-9]{0,15}-[0-9a-f]{4}-[0-9a-f]{10}$");

    private IdGen() {
    }

    /** v0.0.1 🍊 New random agent id; never returns the reserved agent-0000. */
    public static AgentId newAgentId() {
        return new AgentId("agent-" + nonZeroHex4());
    }

    /** v0.0.1 🍊 New random human user id of the form user-xxxx. */
    public static String newUserId() {
        return "user-" + nonZeroHex4();
    }

    /** v0.0.1 🍊 New random room id of the form room-xxxx. */
    public static String newRoomId() {
        return "room-" + nonZeroHex4();
    }

    /** v0.0.1 🍊 New record id {@code <dataName>-<agentHex>-<10hex>} owned by the given agent. */
    public static String recordId(DataName dataName, AgentId owner) {
        long random = ThreadLocalRandom.current().nextLong(RECORD_SPACE);
        return dataName.prefix() + "-" + owner.hex() + "-" + String.format(Locale.ROOT, "%010x", random);
    }

    /** v0.0.1 🍊 True when the string matches the uniform record id format. */
    public static boolean isRecordId(String id) {
        return id != null && RECORD_FORMAT.matcher(id).matches();
    }

    /** v0.0.1 🍊 Four lowercase hex digits drawn from a secure RNG, excluding 0000. */
    private static String nonZeroHex4() {
        int value = 1 + SECURE.nextInt(0xFFFF);
        return String.format(Locale.ROOT, "%04x", value);
    }
}
