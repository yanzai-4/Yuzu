package ai.yuzu.common.error;

import java.util.Map;

/**
 * v0.0.1 🍊 JSON body of every error returned to the frontend (REST and realtime).
 *
 * @param code    stable {@link ErrorCode} name the UI switches on
 * @param message human-readable explanation
 * @param details structured context (field errors, limits, agent id, ...)
 * @param agentId agent the error belongs to, or null
 * @param time    natural-language time the error happened
 */
public record ApiError(String code, String message, Map<String, Object> details, String agentId, String time) {

    /** v0.0.1 🍊 Builds an ApiError from a YuzuException. */
    public static ApiError of(YuzuException e, String time) {
        return new ApiError(e.code().name(), e.getMessage(), e.details(), e.agentId(), time);
    }
}
