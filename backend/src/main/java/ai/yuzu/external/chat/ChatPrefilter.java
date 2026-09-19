package ai.yuzu.external.chat;

import ai.yuzu.chat.AuthorKind;
import ai.yuzu.chat.ChatMessage;

/**
 * v0.0.15 🍊 Zero-cost code filter applied before any model call: which new messages an agent must evaluate.
 */
public final class ChatPrefilter {

    /** v0.0.15 🍊 Classification of a message for one agent. */
    public enum Verdict {
        /** v0.0.15 🍊 Not for evaluation and not interesting (own message, card answer, system, warning). */
        DROP,
        /** v0.0.15 🍊 Visible as context in the chat window only (closed topics, deep agent chains). */
        CONTEXT_ONLY,
        /** v0.0.15 🍊 Must be evaluated by the chat module. */
        EVALUATE
    }

    private ChatPrefilter() {
    }

    /** v0.0.15 🍊 Classifies a message for an agent. */
    public static Verdict classify(ChatMessage message, String agentId, boolean pairSuppressed) {
        if (message.authorId().equals(agentId) || !message.fanout()) {
            return Verdict.DROP;
        }
        if (message.authorKind() == AuthorKind.AGENT
                && (message.closure() || message.causalDepth() >= LoopGuard.MAX_DEPTH || pairSuppressed)) {
            return Verdict.CONTEXT_ONLY;
        }
        return Verdict.EVALUATE;
    }
}
