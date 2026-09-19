package ai.yuzu.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v0.0.1 🍊 Base of every expected failure in Yuzu; carries a stable {@link ErrorCode} and details.
 *
 * <p>Risky functions throw a subclass; REST handlers and the realtime error reporter turn it into a
 * frontend-visible error without losing the code or details.</p>
 */
public class YuzuException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details = new LinkedHashMap<>();
    private String agentId;

    /** v0.0.1 🍊 Creates an exception with the code's default message. */
    public YuzuException(ErrorCode code) {
        this(code, code.defaultMessage(), null);
    }

    /** v0.0.1 🍊 Creates an exception with a specific message. */
    public YuzuException(ErrorCode code, String message) {
        this(code, message, null);
    }

    /** v0.0.1 🍊 Creates an exception with a specific message and cause. */
    public YuzuException(ErrorCode code, String message, Throwable cause) {
        super(message == null ? code.defaultMessage() : message, cause);
        this.code = code;
    }

    /** v0.0.1 🍊 Adds a detail entry (fluent; only used before the exception is thrown). */
    public YuzuException with(String key, Object value) {
        if (key != null && value != null) {
            details.put(key, value);
        }
        return this;
    }

    /** v0.0.1 🍊 Attaches the agent the failure belongs to (fluent). */
    public YuzuException forAgent(String agentId) {
        this.agentId = agentId;
        return this;
    }

    /** v0.0.1 🍊 The stable error code. */
    public ErrorCode code() {
        return code;
    }

    /** v0.0.1 🍊 Read-only view of the details. */
    public Map<String, Object> details() {
        return Collections.unmodifiableMap(details);
    }

    /** v0.0.1 🍊 The owning agent id, or null for platform-level failures. */
    public String agentId() {
        return agentId;
    }
}
