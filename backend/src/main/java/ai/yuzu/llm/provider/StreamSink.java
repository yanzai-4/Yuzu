package ai.yuzu.llm.provider;

import ai.yuzu.common.concurrent.CancelToken;

/** v0.0.8 🍊 Receives text deltas while a streamed completion is generated. */
@FunctionalInterface
public interface StreamSink {

    /** v0.0.8 🍊 Called for every non-empty text delta, in order. */
    void onDelta(String delta);

    /**
     * v0.0.30 🍊 Wraps a sink so every delta first checks the cancel token: an interrupted answer stops at the
     * next chunk instead of streaming on, and nothing cancelled ever reaches the UI. Null stays null.
     */
    static StreamSink guarded(StreamSink delegate, CancelToken cancel) {
        if (delegate == null || cancel == null) {
            return delegate;
        }
        return delta -> {
            cancel.throwIfCancelled();
            delegate.onDelta(delta);
        };
    }
}
