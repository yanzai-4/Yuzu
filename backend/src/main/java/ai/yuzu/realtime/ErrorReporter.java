package ai.yuzu.realtime;

import ai.yuzu.common.concurrent.ErrorSink;
import ai.yuzu.common.error.ApiError;
import ai.yuzu.common.error.CancelledException;
import ai.yuzu.common.error.ErrorCode;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.common.time.NaturalTime;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v0.0.3 🍊 Pushes every asynchronous failure to the frontend as an {@code error} event (toast + trace log).
 *
 * <p>Cancellations are expected and are not reported. Unexpected exceptions are reported as INTERNAL
 * with their type only (no stack traces or secrets leave the server).</p>
 */
@Component
public class ErrorReporter implements ErrorSink {

    private final SseHub hub;
    private final NaturalTime time;

    /** v0.0.3 🍊 Injects the hub and the time renderer. */
    public ErrorReporter(SseHub hub, NaturalTime time) {
        this.hub = hub;
        this.time = time;
    }

    /** v0.0.3 🍊 Converts the failure to an ApiError and publishes it to every room. */
    @Override
    public void report(String context, String agentId, Throwable error) {
        if (error instanceof CancelledException) {
            return;
        }
        hub.publishAll(EventType.ERROR, agentId, toApiError(context, agentId, error));
    }

    /** v0.0.3 🍊 Publishes an already-built error (used by modules that degrade gracefully). */
    public void publish(String roomId, ApiError error) {
        hub.publish(roomId, EventType.ERROR, error.agentId(), error);
    }

    /** v0.0.3 🍊 Maps any throwable to the public error shape, adding the failing context. */
    public ApiError toApiError(String context, String agentId, Throwable error) {
        if (error instanceof YuzuException ye) {
            ApiError base = ApiError.of(ye, time.now());
            Map<String, Object> details = new LinkedHashMap<>(base.details());
            details.put("context", context);
            return new ApiError(base.code(), base.message(), details,
                    base.agentId() != null ? base.agentId() : agentId, base.time());
        }
        return new ApiError(ErrorCode.INTERNAL.name(), ErrorCode.INTERNAL.defaultMessage(),
                Map.of("context", context, "type", error.getClass().getSimpleName()), agentId, time.now());
    }
}
