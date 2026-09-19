package ai.yuzu.common.concurrent;

/**
 * v0.0.1 🍊 Receives failures that happen off the request thread (agent loops, tools, timers).
 *
 * <p>Implementations log, persist, or push the error to the frontend in real time. Every
 * implementation must be fast and must never throw.</p>
 */
public interface ErrorSink {

    /**
     * v0.0.1 🍊 Reports an asynchronous failure.
     *
     * @param context short label of what was running (for example "main-loop")
     * @param agentId owning agent id, or null for platform tasks
     * @param error   the failure
     */
    void report(String context, String agentId, Throwable error);
}
