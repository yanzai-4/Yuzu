package ai.yuzu.common.error;

/** v0.0.1 🍊 Thrown on optimistic-lock conflicts or invalid state transitions. */
public class ConflictException extends YuzuException {

    /** v0.0.1 🍊 Creates the exception with the code's default message. */
    public ConflictException() {
        super(ErrorCode.CONFLICT);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message. */
    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message and cause. */
    public ConflictException(String message, Throwable cause) {
        super(ErrorCode.CONFLICT, message, cause);
    }
}
