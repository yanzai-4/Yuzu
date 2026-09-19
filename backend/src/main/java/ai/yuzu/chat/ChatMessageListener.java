package ai.yuzu.chat;

/**
 * v0.0.5 🍊 Receives every new chat message after it is persisted and published (agent fan-out hooks in here).
 *
 * <p>Listeners are invoked asynchronously on virtual threads; they must never block the poster.</p>
 */
public interface ChatMessageListener {

    /** v0.0.5 🍊 Called once per new message. */
    void onMessage(ChatMessage message);
}
