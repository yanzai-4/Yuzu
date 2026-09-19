package ai.yuzu.common.error;

/** v0.0.1 🍊 Thrown when a workgroup already has the maximum number of agents. */
public class AgentLimitException extends YuzuException {

    /** v0.0.1 🍊 Creates the exception with the code's default message. */
    public AgentLimitException() {
        super(ErrorCode.AGENT_LIMIT);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message. */
    public AgentLimitException(String message) {
        super(ErrorCode.AGENT_LIMIT, message);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message and cause. */
    public AgentLimitException(String message, Throwable cause) {
        super(ErrorCode.AGENT_LIMIT, message, cause);
    }
}
