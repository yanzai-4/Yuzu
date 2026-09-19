package ai.yuzu.llm;

import ai.yuzu.llm.provider.LlmMessage;
import ai.yuzu.llm.provider.ProviderEndpoint;
import ai.yuzu.settings.TierSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * v0.0.8 🍊 Everything the executor needs for one logical model call (before capability shaping).
 *
 * @param schemaName     schema name for structured calls (null for free text)
 * @param schema         strict JSON schema for structured calls (null for free text)
 * @param promptCacheKey cache-routing key (sent only when the endpoint accepts it)
 * @param stream         stream the answer (free-text chat posts)
 * @param temperature    optional temperature (sent only when the model accepts it)
 * @param extra          provider-specific fields merged into the body (may be null)
 */
public record LlmCall(ProviderEndpoint endpoint, TierSettings tier, List<LlmMessage> messages, String schemaName,
                      JsonNode schema, String promptCacheKey, boolean stream, Double temperature, ObjectNode extra) {

    /** v0.0.8 🍊 Defensive copy of the messages. */
    public LlmCall {
        messages = List.copyOf(messages);
    }

    /** v0.0.8 🍊 True when a JSON answer is required. */
    public boolean structured() {
        return schema != null;
    }

    /** v0.0.8 🍊 Copy with different messages (retries append feedback at the end). */
    public LlmCall withMessages(List<LlmMessage> next) {
        return new LlmCall(endpoint, tier, next, schemaName, schema, promptCacheKey, stream, temperature, extra);
    }

    /** v0.0.8 🍊 Copy with a different tier budget (used after finish_reason=length). */
    public LlmCall withTier(TierSettings next) {
        return new LlmCall(endpoint, next, messages, schemaName, schema, promptCacheKey, stream, temperature, extra);
    }
}
