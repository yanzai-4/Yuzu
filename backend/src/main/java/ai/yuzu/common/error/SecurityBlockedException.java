package ai.yuzu.common.error;

/** v0.0.1 🍊 Thrown when the safety review blocks content. */
public class SecurityBlockedException extends YuzuException {

    /** v0.0.1 🍊 Creates the exception with the code's default message. */
    public SecurityBlockedException() {
        super(ErrorCode.SECURITY_BLOCKED);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message. */
    public SecurityBlockedException(String message) {
        super(ErrorCode.SECURITY_BLOCKED, message);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message and cause. */
    public SecurityBlockedException(String message, Throwable cause) {
        super(ErrorCode.SECURITY_BLOCKED, message, cause);
    }
}
