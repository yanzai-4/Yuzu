package ai.yuzu.common.error;

/** v0.0.1 🍊 Thrown when a required setting (such as the API key) is missing. */
public class NotConfiguredException extends YuzuException {

    /** v0.0.1 🍊 Creates the exception with the code's default message. */
    public NotConfiguredException() {
        super(ErrorCode.NOT_CONFIGURED);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message. */
    public NotConfiguredException(String message) {
        super(ErrorCode.NOT_CONFIGURED, message);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message and cause. */
    public NotConfiguredException(String message, Throwable cause) {
        super(ErrorCode.NOT_CONFIGURED, message, cause);
    }
}
