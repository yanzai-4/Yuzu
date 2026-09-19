package ai.yuzu.chat;

/** v0.0.5 🍊 Streaming state of a message whose text is still being generated. */
public enum StreamState {
    NONE,
    STREAMING,
    DONE,
    STOPPED
}
