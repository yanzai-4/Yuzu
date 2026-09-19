package ai.yuzu.chat;

import ai.yuzu.bootstrap.SnapshotBuilder;
import ai.yuzu.bootstrap.SnapshotContributor;
import org.springframework.stereotype.Component;

/** v0.0.5 🍊 Adds the latest 100 chat messages (from the in-memory window) to the bootstrap snapshot. */
@Component
public class ChatSnapshotContributor implements SnapshotContributor {

    private static final int BOOTSTRAP_MESSAGES = 100;

    private final ChatService chat;

    /** v0.0.5 🍊 Injects the chat service. */
    public ChatSnapshotContributor(ChatService chat) {
        this.chat = chat;
    }

    /** v0.0.5 🍊 Contributes messages. */
    @Override
    public void contribute(String roomId, SnapshotBuilder snapshot) {
        snapshot.addAll("messages", chat.history(roomId, null, BOOTSTRAP_MESSAGES));
    }
}
