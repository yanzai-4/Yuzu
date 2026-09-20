package ai.yuzu.external.chat;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.Permission;
import ai.yuzu.chat.AuthorKind;
import ai.yuzu.chat.ChatMessage;

import java.util.Locale;

/**
 * v0.0.34 🍊 Zero-cost code filter applied before any model call: which new messages an agent must evaluate.
 *
 * <p>Intake belongs to the project manager: a human request that names nobody is evaluated ONLY by the agents
 * that may assign work ({@link Permission#TASK_ASSIGN}, the PM preset). Everyone else sees it as context. This
 * is enforced here, in code, so a chattier model cannot talk its way into work that was never given to it.</p>
 *
 * <p>A human always reaches a coworker directly by @mentioning it, with @all, by writing its name, or by
 * speaking right after it (the answer to the question that coworker just asked). A workgroup without any
 * agent that may assign work falls back to "everyone evaluates", so a request is never silently dropped.</p>
 */
public final class ChatPrefilter {

    /** v0.0.15 🍊 Classification of a message for one agent. */
    public enum Verdict {
        /** v0.0.15 🍊 Not for evaluation and not interesting (own message, card answer, system, warning). */
        DROP,
        /** v0.0.15 🍊 Visible as context in the chat window only (closed topics, deep chains, other people's requests). */
        CONTEXT_ONLY,
        /** v0.0.15 🍊 Must be evaluated by the chat module. */
        EVALUATE
    }

    /**
     * v0.0.34 🍊 What the room looks like around a new message (computed once per message, for every agent).
     *
     * @param hasIntakeOwner     true when at least one present agent of the room may assign work
     * @param previousAuthorId   author of the message before this one (null when it is the first)
     */
    public record RoomContext(boolean hasIntakeOwner, String previousAuthorId) {
    }

    private ChatPrefilter() {
    }

    /** v0.0.34 🍊 Classifies a message for an agent. */
    public static Verdict classify(ChatMessage message, AgentProfile agent, boolean pairSuppressed,
                                   RoomContext room) {
        String agentId = agent.agentId().value();
        if (message.authorId().equals(agentId) || !message.fanout()) {
            return Verdict.DROP;
        }
        if (message.authorKind() == AuthorKind.AGENT
                && (message.closure() || message.causalDepth() >= LoopGuard.MAX_DEPTH || pairSuppressed)) {
            return Verdict.CONTEXT_ONLY;
        }
        if (message.authorKind() == AuthorKind.HUMAN && room.hasIntakeOwner() && !ownsIntake(agent)
                && !addresses(message, agent, room)) {
            return Verdict.CONTEXT_ONLY;
        }
        return Verdict.EVALUATE;
    }

    /** v0.0.34 🍊 True for an agent that may take in and assign human work (the project manager). */
    public static boolean ownsIntake(AgentProfile agent) {
        return agent.scope().has(Permission.TASK_ASSIGN);
    }

    /** v0.0.34 🍊 True when a human message is meant for this agent: @mention, @all, its name, or a direct answer. */
    private static boolean addresses(ChatMessage message, AgentProfile agent, RoomContext room) {
        String agentId = agent.agentId().value();
        return message.mentions(agentId)
                || agentId.equals(room.previousAuthorId())
                || namesAgent(message.content(), agent.name());
    }

    /** v0.0.34 🍊 True when the text names the coworker without a formal @mention ("Lime, can you …"). */
    private static boolean namesAgent(String content, String name) {
        if (content == null || name == null || name.isBlank()) {
            return false;
        }
        String haystack = content.toLowerCase(Locale.ROOT);
        String needle = name.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return false;
            }
            boolean startFree = at == 0 || !Character.isLetterOrDigit(haystack.charAt(at - 1));
            int end = at + needle.length();
            boolean endFree = end >= haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (startFree && endFree) {
                return true;
            }
            from = at + 1;
        }
    }
}
