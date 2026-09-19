package ai.yuzu.llm.usage;

/**
 * v0.0.11 🍊 Aggregated usage of one key (contract type {@code UsageRow}).
 *
 * @param hitRate cached ÷ prompt over calls whose provider reported caching; null when unknown
 */
public record UsageRow(String key, long calls, long attempts, long promptTokens, long cachedTokens,
                       long completionTokens, long reasoningTokens, long errors, long retries, Double hitRate) {
}
