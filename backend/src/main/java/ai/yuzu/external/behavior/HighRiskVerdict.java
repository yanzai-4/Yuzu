package ai.yuzu.external.behavior;

import ai.yuzu.llm.structured.Desc;

import java.util.List;

/** v0.0.18 🍊 Output of the high-risk second review. */
public record HighRiskVerdict(
        @Desc("Brief reasoning") String reasoning,
        @Desc("true when the high-risk calls may run") boolean approve,
        @Desc("true when a human should confirm before they run") boolean needsHuman,
        @Desc("Concerns, briefly (empty when none)") List<String> concerns) {
}
