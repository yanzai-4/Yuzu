package ai.yuzu.llm.provider;

import ai.yuzu.common.error.ErrorCode;
import ai.yuzu.common.error.YuzuException;

/**
 * v0.0.8 🍊 The provider rejected the request itself (HTTP 400/404/422): wrong parameter, unknown model, ...
 *
 * <p>{@link #param()} names the offending parameter when the provider says so; the executor uses it to
 * learn model capabilities (drop temperature, switch max_tokens, downgrade response_format) and resend.</p>
 */
public class ProviderRequestException extends YuzuException {

    private final int status;
    private final String param;
    private final String providerCode;

    /** v0.0.8 🍊 Creates the exception from a parsed provider error body. */
    public ProviderRequestException(int status, String message, String param, String providerCode) {
        super(ErrorCode.LLM_TRANSPORT, message);
        this.status = status;
        this.param = param;
        this.providerCode = providerCode;
        with("httpStatus", status);
        if (param != null) {
            with("param", param);
        }
    }

    /** v0.0.8 🍊 HTTP status. */
    public int status() {
        return status;
    }

    /** v0.0.8 🍊 Offending parameter reported by the provider, or null. */
    public String param() {
        return param;
    }

    /** v0.0.8 🍊 Provider error code (for example "context_length_exceeded"), or null. */
    public String providerCode() {
        return providerCode;
    }
}
