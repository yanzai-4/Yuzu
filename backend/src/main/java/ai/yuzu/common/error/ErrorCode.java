package ai.yuzu.common.error;

import org.springframework.http.HttpStatus;

/**
 * v0.0.1 🍊 Stable error codes shared by REST responses and realtime error events.
 *
 * <p>The frontend switches on {@link #name()}; never rename a constant, only add new ones.</p>
 */
public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "The request is invalid."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource does not exist."),
    CONFLICT(HttpStatus.CONFLICT, "The resource was changed concurrently or is in the wrong state."),
    AGENT_LIMIT(HttpStatus.CONFLICT, "A workgroup can have at most 8 agents."),
    PERMISSION_DENIED(HttpStatus.FORBIDDEN, "The agent is not allowed to do this."),
    SANDBOX_VIOLATION(HttpStatus.FORBIDDEN, "The operation tried to leave the agent workspace."),
    SECURITY_BLOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "The content was blocked by the safety review."),
    APPROVAL_REQUIRED(HttpStatus.ACCEPTED, "A human approval is required before this can run."),
    NOT_CONFIGURED(HttpStatus.PRECONDITION_FAILED, "The platform is not configured yet (for example, no API key)."),
    LLM_AUTH(HttpStatus.BAD_GATEWAY, "The model provider rejected the API key."),
    LLM_TRANSPORT(HttpStatus.BAD_GATEWAY, "The model provider could not be reached."),
    LLM_OUTPUT_INVALID(HttpStatus.BAD_GATEWAY, "The model kept returning output in the wrong format."),
    BUDGET_EXHAUSTED(HttpStatus.TOO_MANY_REQUESTS, "The model budget is used up; calls are paused."),
    TOOL_EXECUTION(HttpStatus.INTERNAL_SERVER_ERROR, "A tool failed while executing."),
    CANCELLED(HttpStatus.CONFLICT, "The operation was cancelled."),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal error occurred.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    /** v0.0.1 🍊 HTTP status used when this error reaches a REST client. */
    public HttpStatus status() {
        return status;
    }

    /** v0.0.1 🍊 Human-readable fallback message. */
    public String defaultMessage() {
        return defaultMessage;
    }
}
