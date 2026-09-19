package ai.yuzu.external.chat;

import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.Nullable;

/** v0.0.15 🍊 Output of the chat triage module. */
public record ChatDecision(
        @Desc("Brief reasoning: who the new message is for and whether it concerns you") String reasoning,
        Decision decision,
        @Nullable @Desc("REPLY only: the reply, starting with an @mention of the person you answer") String replyText,
        @Nullable @Desc("FORWARD of a human's work request only: short acknowledgement starting with an @mention")
        String ackText,
        @Desc("true when your REPLY closes the topic and no answer is expected") boolean topicClosed) {

    /** v0.0.15 🍊 The triage decision. */
    public enum Decision { IGNORE, REPLY, FORWARD }
}
