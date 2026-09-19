package ai.yuzu.llm.provider;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * v0.0.8 🍊 A fully shaped provider request (after capabilities decided which parameters to send).
 *
 * @param maxTokensField    "max_completion_tokens" or "max_tokens"
 * @param temperature       null = not sent
 * @param reasoningEffort   null = not sent
 * @param promptCacheKey    null = not sent
 * @param streamUsage       send {@code stream_options.include_usage} when streaming
 * @param extra             provider-specific fields merged last (may be null)
 */
public record LlmRequest(String model, List<LlmMessage> messages, ResponseFormat format, int maxOutputTokens,
                         String maxTokensField, Double temperature, String reasoningEffort, String promptCacheKey,
                         boolean stream, boolean streamUsage, ObjectNode extra) {

    /** v0.0.8 🍊 Defensive copy of the messages. */
    public LlmRequest {
        messages = List.copyOf(messages);
        format = format == null ? ResponseFormat.NONE : format;
    }
}
