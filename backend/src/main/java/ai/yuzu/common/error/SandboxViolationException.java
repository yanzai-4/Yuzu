package ai.yuzu.common.error;

/** v0.0.1 🍊 Thrown when a path or process tries to escape the agent workspace. */
public class SandboxViolationException extends YuzuException {

    /** v0.0.1 🍊 Creates the exception with the code's default message. */
    public SandboxViolationException() {
        super(ErrorCode.SANDBOX_VIOLATION);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message. */
    public SandboxViolationException(String message) {
        super(ErrorCode.SANDBOX_VIOLATION, message);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message and cause. */
    public SandboxViolationException(String message, Throwable cause) {
        super(ErrorCode.SANDBOX_VIOLATION, message, cause);
    }
}
