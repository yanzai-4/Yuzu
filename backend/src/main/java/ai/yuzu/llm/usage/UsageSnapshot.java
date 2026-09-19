package ai.yuzu.llm.usage;

import java.util.List;

/** v0.0.11 🍊 Usage totals and breakdowns (contract type {@code UsageSnapshot}). */
public record UsageSnapshot(UsageRow totals, List<UsageRow> byAgent, List<UsageRow> byModule, List<UsageRow> byTier,
                            List<UsageRow> byModel, long localCacheHits, long localCacheLookups, String time) {
}
