package ai.yuzu.chat;

import java.util.List;

/** v0.0.5 🍊 API shape of a chat message (contract type {@code ChatMessage}). */
public record ChatMessageView(String id, String roomId, long seq, AuthorKind authorKind, String authorId,
                              String authorName, MessageKind kind, String content, List<String> mentions,
                              boolean mentionAll, boolean closure, int causalDepth, String replyTo, String cardId,
                              StreamState streamState, String traceId, String time) {
}
