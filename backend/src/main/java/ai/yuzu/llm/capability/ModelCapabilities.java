package ai.yuzu.llm.capability;

import ai.yuzu.llm.structured.OutputStrategy;

/**
 * v0.0.8 🍊 What a (base URL, model) pair accepts; seeded by rules, then learned from provider errors.
 *
 * @param temperature     the model accepts {@code temperature}
 * @param maxTokensField  "max_completion_tokens" or "max_tokens"
 * @param reasoningEffort the model accepts {@code reasoning_effort}
 * @param streamUsage     the endpoint accepts {@code stream_options.include_usage}
 * @param promptCacheKey  the endpoint accepts {@code prompt_cache_key}
 * @param strategy        strongest JSON strategy that works
 * @param strictFailures  consecutive validation failures while using strict schemas (reliability signal)
 */
public record ModelCapabilities(boolean temperature, String maxTokensField, boolean reasoningEffort,
                                boolean streamUsage, boolean promptCacheKey, OutputStrategy strategy,
                                int strictFailures) {

    /** v0.0.8 🍊 Copy with a different temperature flag. */
    public ModelCapabilities withTemperature(boolean value) {
        return new ModelCapabilities(value, maxTokensField, reasoningEffort, streamUsage, promptCacheKey, strategy,
                strictFailures);
    }

    /** v0.0.8 🍊 Copy with a different max-tokens field. */
    public ModelCapabilities withMaxTokensField(String value) {
        return new ModelCapabilities(temperature, value, reasoningEffort, streamUsage, promptCacheKey, strategy,
                strictFailures);
    }

    /** v0.0.8 🍊 Copy with a different reasoning-effort flag. */
    public ModelCapabilities withReasoningEffort(boolean value) {
        return new ModelCapabilities(temperature, maxTokensField, value, streamUsage, promptCacheKey, strategy,
                strictFailures);
    }

    /** v0.0.8 🍊 Copy with a different stream-usage flag. */
    public ModelCapabilities withStreamUsage(boolean value) {
        return new ModelCapabilities(temperature, maxTokensField, reasoningEffort, value, promptCacheKey, strategy,
                strictFailures);
    }

    /** v0.0.8 🍊 Copy with a different prompt-cache-key flag. */
    public ModelCapabilities withPromptCacheKey(boolean value) {
        return new ModelCapabilities(temperature, maxTokensField, reasoningEffort, streamUsage, value, strategy,
                strictFailures);
    }

    /** v0.0.8 🍊 Copy with a different JSON strategy (resets the failure counter). */
    public ModelCapabilities withStrategy(OutputStrategy value) {
        return new ModelCapabilities(temperature, maxTokensField, reasoningEffort, streamUsage, promptCacheKey, value,
                0);
    }

    /** v0.0.8 🍊 Copy with a different strict-failure counter. */
    public ModelCapabilities withStrictFailures(int value) {
        return new ModelCapabilities(temperature, maxTokensField, reasoningEffort, streamUsage, promptCacheKey,
                strategy, value);
    }
}
