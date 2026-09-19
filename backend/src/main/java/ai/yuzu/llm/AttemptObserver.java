package ai.yuzu.llm;

import ai.yuzu.llm.provider.LlmRequest;
import ai.yuzu.llm.provider.LlmResult;

/** v0.0.8 🍊 Notified after every HTTP attempt (success or failure) for metering and payload recording. */
public interface AttemptObserver {

    /** v0.0.8 🍊 A request succeeded at the transport level. */
    void onSuccess(LlmRequest request, LlmResult result, int attempt);

    /** v0.0.8 🍊 A request failed (the error may be retried by the executor). */
    void onFailure(LlmRequest request, RuntimeException error, long latencyMs, int attempt);

    /** v0.0.8 🍊 Observer that ignores everything. */
    AttemptObserver NONE = new AttemptObserver() {
        @Override
        public void onSuccess(LlmRequest request, LlmResult result, int attempt) {
        }

        @Override
        public void onFailure(LlmRequest request, RuntimeException error, long latencyMs, int attempt) {
        }
    };
}
