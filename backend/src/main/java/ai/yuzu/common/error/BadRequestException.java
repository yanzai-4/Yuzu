package ai.yuzu.common.error;

/** v0.0.1 🍊 Thrown when a request is syntactically or semantically invalid. */
public class BadRequestException extends YuzuException {

    /** v0.0.1 🍊 Creates the exception with the code's default message. */
    public BadRequestException() {
        super(ErrorCode.BAD_REQUEST);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message. */
    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message and cause. */
    public BadRequestException(String message, Throwable cause) {
        super(ErrorCode.BAD_REQUEST, message, cause);
    }
}
