package ai.yuzu.common.error;

/** v0.0.1 🍊 Thrown when an operation is interrupted or cancelled. */
public class CancelledException extends YuzuException {

    /** v0.0.1 🍊 Creates the exception with the code's default message. */
    public CancelledException() {
        super(ErrorCode.CANCELLED);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message. */
    public CancelledException(String message) {
        super(ErrorCode.CANCELLED, message);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message and cause. */
    public CancelledException(String message, Throwable cause) {
        super(ErrorCode.CANCELLED, message, cause);
    }
}
