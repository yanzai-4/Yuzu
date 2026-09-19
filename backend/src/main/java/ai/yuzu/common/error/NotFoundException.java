package ai.yuzu.common.error;

/** v0.0.1 🍊 Thrown when a requested resource does not exist. */
public class NotFoundException extends YuzuException {

    /** v0.0.1 🍊 Creates the exception with the code's default message. */
    public NotFoundException() {
        super(ErrorCode.NOT_FOUND);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message. */
    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message and cause. */
    public NotFoundException(String message, Throwable cause) {
        super(ErrorCode.NOT_FOUND, message, cause);
    }
}
