package ai.yuzu.common.error;

/** v0.0.1 🍊 Thrown when a tool fails while executing. */
public class ToolExecutionException extends YuzuException {

    /** v0.0.1 🍊 Creates the exception with the code's default message. */
    public ToolExecutionException() {
        super(ErrorCode.TOOL_EXECUTION);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message. */
    public ToolExecutionException(String message) {
        super(ErrorCode.TOOL_EXECUTION, message);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message and cause. */
    public ToolExecutionException(String message, Throwable cause) {
        super(ErrorCode.TOOL_EXECUTION, message, cause);
    }
}
