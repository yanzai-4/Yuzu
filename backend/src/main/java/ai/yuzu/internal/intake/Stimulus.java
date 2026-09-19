package ai.yuzu.internal.intake;

import ai.yuzu.chat.ChatMessage;

import java.util.List;

/**
 * v0.0.16 🍊 Anything from the outside world that enters an agent's internal modules (always via the intake).
 */
public sealed interface Stimulus {

    /** v0.0.16 🍊 Short description for planning/cognition prompts and the monitor. */
    String describe();

    /**
     * v0.0.16 🍊 Group chat forwarded by the chat module.
     *
     * @param earlier chat messages the mind has not seen yet (context), oldest first
     * @param unread  the new messages the chat module forwarded, oldest first
     */
    record ChatStimulus(String roomId, List<ChatMessage> earlier, List<ChatMessage> unread) implements Stimulus {
        @Override
        public String describe() {
            ChatMessage last = unread.getLast();
            return "New group-chat message from " + last.authorName();
        }
    }

    /**
     * v0.0.16 🍊 Results of tools the agent used (already safety-reviewed), with completion times.
     *
     * @param text rendered results including when each tool finished
     */
    record ToolResultsStimulus(String batchId, String text) implements Stimulus {
        @Override
        public String describe() {
            return "Results of my tools";
        }
    }

    /** v0.0.16 🍊 A human answered one of the agent's question cards (never passes through any chat module). */
    record QuestionAnswerStimulus(String cardId, String answeredBy, String text) implements Stimulus {
        @Override
        public String describe() {
            return answeredBy + " answered my question";
        }
    }

    /** v0.0.16 🍊 A notice produced by code for this agent (for example governance hints for the PM). */
    record NoticeStimulus(String source, String text) implements Stimulus {
        @Override
        public String describe() {
            return "Notice from " + source;
        }
    }
}
