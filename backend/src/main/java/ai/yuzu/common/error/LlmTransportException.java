package ai.yuzu.common.error;

/**
 * v0.0.7 🍊 Thrown when the model provider cannot be reached or answers with a transport-level error.
 *
 * <p>{@link #retryable()} is true for 429, 5xx, timeouts and I/O errors (retried with backoff); false for
 * other 4xx responses that retrying cannot fix.</p>
 */
public class LlmTransportException extends YuzuException {

    private final int status;
    private final boolean retryable;
    private final long retryAfterMillis;

    /** v0.0.7 🍊 Creates the exception with the HTTP status (0 for I/O errors) and retry hints. */
    public LlmTransportException(String message, int status, boolean retryable, long retryAfterMillis,
                                 Throwable cause) {
        super(ErrorCode.LLM_TRANSPORT, message, cause);
        this.status = status;
        this.retryable = retryable;
        this.retryAfterMillis = retryAfterMillis;
        with("httpStatus", status);
    }

    /** v0.0.7 🍊 HTTP status of the failed response, or 0 when no response was received. */
    public int status() {
        return status;
    }

    /** v0.0.7 🍊 True when retrying may succeed. */
    public boolean retryable() {
        return retryable;
    }

    /** v0.0.7 🍊 Server-suggested wait before retrying (0 when unknown). */
    public long retryAfterMillis() {
        return retryAfterMillis;
    }
}
