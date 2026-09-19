package ai.yuzu.common.error;

/** v0.0.1 🍊 Thrown when the model provider rejects the credentials (401/403). */
public class LlmAuthException extends YuzuException {

    /** v0.0.1 🍊 Creates the exception with the code's default message. */
    public LlmAuthException() {
        super(ErrorCode.LLM_AUTH);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message. */
    public LlmAuthException(String message) {
        super(ErrorCode.LLM_AUTH, message);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message and cause. */
    public LlmAuthException(String message, Throwable cause) {
        super(ErrorCode.LLM_AUTH, message, cause);
    }
}
