package ai.yuzu.chat;

import ai.yuzu.common.time.NaturalTime;

import java.time.Instant;
import java.util.List;

/**
 * v0.0.5 🍊 Immutable group-chat message (domain form; {@link #toView(NaturalTime)} gives the API form).
 *
 * @param fanout      false for messages no chat module may evaluate (card answers, system, warnings)
 * @param causalDepth 0 for humans; agent posts carry (depth of their trigger + 1) for loop guarding
 * @param closure     true when the author marked the topic as closed ("got it, on it")
 */
public record ChatMessage(String id, String roomId, long seq, AuthorKind authorKind, String authorId,
                          String authorName, MessageKind kind, String content, List<String> mentions,
                          boolean mentionAll, boolean closure, int causalDepth, String replyTo, String cardId,
                          boolean fanout, StreamState streamState, String traceId, Instant createdAt) {

    /** v0.0.5 🍊 Defensive copy of mentions. */
    public ChatMessage {
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
    }

    /** v0.0.5 🍊 True when the message mentions the member (directly or through @all). */
    public boolean mentions(String memberId) {
        return mentionAll || mentions.contains(memberId);
    }

    /** v0.0.5 🍊 Copy with new content and stream state (used while streaming). */
    public ChatMessage withContent(String newContent, StreamState state) {
        return new ChatMessage(id, roomId, seq, authorKind, authorId, authorName, kind, newContent, mentions,
                mentionAll, closure, causalDepth, replyTo, cardId, fanout, state, traceId, createdAt);
    }

    /** v0.0.5 🍊 API view with a natural-language time. */
    public ChatMessageView toView(NaturalTime time) {
        return new ChatMessageView(id, roomId, seq, authorKind, authorId, authorName, kind, content, mentions,
                mentionAll, closure, causalDepth, replyTo, cardId, streamState, traceId, time.compact(createdAt));
    }
}
