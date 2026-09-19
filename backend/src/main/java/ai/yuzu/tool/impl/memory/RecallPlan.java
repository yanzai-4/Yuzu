package ai.yuzu.tool.impl.memory;

import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.Nullable;

import java.util.List;

/** v0.0.19 🍊 Output of the memory-read module: search keywords plus an absolute time range (validated by code). */
public record RecallPlan(
        @Desc("Brief reasoning") String reasoning,
        @Desc("0 to 6 search keywords (names, topics, nouns); empty when recalling purely by time") List<String> keywords,
        @Nullable @Desc("Start of the time range as yyyy-MM-dd HH:mm:ss in the workgroup's local time; null when the recall is not about a time") String fromTime,
        @Nullable @Desc("End of the time range in the same format; null exactly when fromTime is null") String toTime) {
}
