package ai.yuzu.common.error;

/** v0.0.1 🍊 Thrown when structured output is still invalid after all retries. */
public class LlmOutputInvalidException extends YuzuException {

    /** v0.0.1 🍊 Creates the exception with the code's default message. */
    public LlmOutputInvalidException() {
        super(ErrorCode.LLM_OUTPUT_INVALID);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message. */
    public LlmOutputInvalidException(String message) {
        super(ErrorCode.LLM_OUTPUT_INVALID, message);
    }

    /** v0.0.1 🍊 Creates the exception with a specific message and cause. */
    public LlmOutputInvalidException(String message, Throwable cause) {
        super(ErrorCode.LLM_OUTPUT_INVALID, message, cause);
    }
}
