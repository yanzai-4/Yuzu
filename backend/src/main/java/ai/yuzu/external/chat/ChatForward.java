package ai.yuzu.external.chat;

import ai.yuzu.chat.ChatMessage;

import java.util.List;

/**
 * v0.0.15 🍊 What the chat module hands to the internal modules: the window, the new messages, the newest one.
 *
 * @param window context before the new messages (anchored, 20-29 messages)
 * @param unread the new messages that were evaluated (oldest first)
 * @param newest the most recent new message
 */
public record ChatForward(String roomId, List<ChatMessage> window, List<ChatMessage> unread, ChatMessage newest) {
}
