package ai.yuzu.common.id;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.regex.Pattern;

/**
 * v0.0.1 🍊 Validated agent identifier of the form {@code agent-xxxx} (4 lowercase hex digits).
 *
 * <p>{@code agent-0000} is reserved for rows produced by humans or the system, so every record id
 * keeps the uniform {@code <name>-<4hex>-<10hex>} shape.</p>
 */
public record AgentId(String value) {

    private static final Pattern FORMAT = Pattern.compile("^agent-[0-9a-f]{4}$");

    /** v0.0.1 🍊 Reserved owner id for human- or system-originated records. */
    public static final AgentId SYSTEM = new AgentId("agent-0000");

    /** v0.0.1 🍊 Rejects anything that is not exactly agent-xxxx. */
    public AgentId {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid agent id: " + value);
        }
    }

    /** v0.0.1 🍊 Parses and validates an agent id (used by JSON and path binding). */
    @JsonCreator
    public static AgentId of(String value) {
        return new AgentId(value);
    }

    /** v0.0.1 🍊 Returns true when the string is a syntactically valid agent id. */
    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    /** v0.0.1 🍊 The 4-hex suffix embedded into every record id owned by this agent. */
    public String hex() {
        return value.substring(6);
    }

    /** v0.0.1 🍊 True for the reserved human/system owner id. */
    public boolean isSystem() {
        return SYSTEM.value.equals(value);
    }

    /** v0.0.1 🍊 Serializes as the plain string value. */
    @JsonValue
    @Override
    public String toString() {
        return value;
    }
}
