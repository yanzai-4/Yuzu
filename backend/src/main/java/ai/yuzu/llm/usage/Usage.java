package ai.yuzu.llm.usage;

/**
 * v0.0.8 🍊 Normalized token usage of one provider call.
 *
 * @param cachedTokens     prompt tokens served from the provider's prompt cache
 * @param cacheWriteTokens prompt tokens written to the cache (providers that report it)
 * @param estimated        true when the provider sent no usage and the numbers were estimated locally
 * @param cacheReported    true when the provider reported any cache field (only these count for hit rate)
 */
public record Usage(int promptTokens, int cachedTokens, int cacheWriteTokens, int completionTokens,
                    int reasoningTokens, boolean estimated, boolean cacheReported) {

    /** v0.0.8 🍊 Zero usage. */
    public static final Usage NONE = new Usage(0, 0, 0, 0, 0, false, false);
}
