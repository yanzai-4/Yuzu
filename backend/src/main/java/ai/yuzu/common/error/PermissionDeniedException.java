package ai.yuzu.common.error;

/** v0.0.1 🍊 Thrown by code-level permission guards when an agent lacks a permission or limit. */
public class PermissionDeniedException extends YuzuException {

    /** v0.0.1 🍊 Creates the exception with the code's default message. */
    public PermissionDeniedException() {
        super(ErrorCode.PERMISSION_DENIED);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message. */
    public PermissionDeniedException(String message) {
        super(ErrorCode.PERMISSION_DENIED, message);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message and cause. */
    public PermissionDeniedException(String message, Throwable cause) {
        super(ErrorCode.PERMISSION_DENIED, message, cause);
    }
}
