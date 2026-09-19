package ai.yuzu.llm.provider;

/** v0.0.8 🍊 Receives text deltas while a streamed completion is generated. */
@FunctionalInterface
public interface StreamSink {

    /** v0.0.8 🍊 Called for every non-empty text delta, in order. */
    void onDelta(String delta);
}
