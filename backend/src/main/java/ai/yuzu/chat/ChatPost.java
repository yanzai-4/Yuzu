package ai.yuzu.chat;

import java.util.List;

/**
 * v0.0.5 🍊 Everything needed to create a chat message; built with the static factories.
 *
 * @param mentionTargetsOverride when non-null, replaces parsed mentions (used by agents that must @ someone)
 */
public record ChatPost(String roomId, AuthorKind authorKind, String authorId, String authorName, MessageKind kind,
                       String content, int causalDepth, boolean closure, String replyTo, String cardId,
                       boolean fanout, StreamState streamState, String traceId,
                       List<String> mentionTargetsOverride) {

    /** v0.0.5 🍊 A plain message from a human (depth 0, fanned out to every agent). */
    public static ChatPost human(String roomId, String userId, String username, String content) {
        return new ChatPost(roomId, AuthorKind.HUMAN, userId, username, MessageKind.TEXT, content, 0, false, null,
                null, true, StreamState.NONE, null, null);
    }

    /** v0.0.5 🍊 A plain message from an agent. */
    public static ChatPost agent(String roomId, String agentId, String agentName, String content, int causalDepth,
                                 boolean closure, String traceId) {
        return new ChatPost(roomId, AuthorKind.AGENT, agentId, agentName, MessageKind.TEXT, content, causalDepth,
                closure, null, null, true, StreamState.NONE, traceId, null);
    }

    /** v0.0.5 🍊 A yellow security notice written on behalf of an agent (never fanned out). */
    public static ChatPost warning(String roomId, String agentId, String agentName, String content, String traceId) {
        return new ChatPost(roomId, AuthorKind.AGENT, agentId, agentName, MessageKind.WARNING, content, 0, true, null,
                null, false, StreamState.NONE, traceId, null);
    }

    /** v0.0.5 🍊 A muted system line (never fanned out). */
    public static ChatPost system(String roomId, String content) {
        return new ChatPost(roomId, AuthorKind.SYSTEM, "system", "Yuzu HQ", MessageKind.SYSTEM, content, 0, true, null,
                null, false, StreamState.NONE, null, null);
    }

    /** v0.0.5 🍊 Copy with a different kind (REPORT, cards, ...). */
    public ChatPost withKind(MessageKind newKind) {
        return new ChatPost(roomId, authorKind, authorId, authorName, newKind, content, causalDepth, closure, replyTo,
                cardId, fanout, streamState, traceId, mentionTargetsOverride);
    }

    /** v0.0.5 🍊 Copy attached to a card and excluded from fan-out. */
    public ChatPost withCard(String newCardId) {
        return new ChatPost(roomId, authorKind, authorId, authorName, kind, content, causalDepth, closure, replyTo,
                newCardId, false, streamState, traceId, mentionTargetsOverride);
    }

    /** v0.0.5 🍊 Copy that starts in STREAMING state. */
    public ChatPost streaming() {
        return new ChatPost(roomId, authorKind, authorId, authorName, kind, content, causalDepth, closure, replyTo,
                cardId, fanout, StreamState.STREAMING, traceId, mentionTargetsOverride);
    }

    /** v0.0.5 🍊 Copy replying to a message. */
    public ChatPost replyingTo(String messageId) {
        return new ChatPost(roomId, authorKind, authorId, authorName, kind, content, causalDepth, closure, messageId,
                cardId, fanout, streamState, traceId, mentionTargetsOverride);
    }
}
