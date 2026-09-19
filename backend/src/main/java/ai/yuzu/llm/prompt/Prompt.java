package ai.yuzu.llm.prompt;

import ai.yuzu.llm.provider.LlmMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.0.9 🍊 A rendered prompt: the messages sent to the model plus the rendered time line.
 *
 * <p>Retries append messages at the end ({@link #withRetry}) so the cached prefix stays byte-identical.</p>
 */
public record Prompt(List<LlmMessage> messages) {

    /** v0.0.9 🍊 Defensive copy. */
    public Prompt {
        messages = List.copyOf(messages);
    }

    /** v0.0.9 🍊 Copy with the invalid answer replayed and correction feedback appended at the end. */
    public Prompt withRetry(String invalidAnswer, String feedback) {
        List<LlmMessage> next = new ArrayList<>(messages);
        next.add(LlmMessage.assistant(invalidAnswer == null || invalidAnswer.isBlank() ? "(empty answer)"
                : invalidAnswer.length() > 2_000 ? invalidAnswer.substring(0, 2_000) : invalidAnswer));
        next.add(LlmMessage.user(feedback));
        return new Prompt(next);
    }

    /** v0.0.9 🍊 The system message text (handbook + module instructions). */
    public String systemText() {
        return messages.isEmpty() ? "" : messages.getFirst().content();
    }
}
