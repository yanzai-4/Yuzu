package ai.yuzu.external.behavior;

import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.Nullable;

import java.util.List;

/** v0.0.18 🍊 Output of the behavior review. */
public record BehaviorVerdict(
        @Desc("Brief reasoning over every action") String reasoning,
        @Desc("true only when EVERY action is compliant") boolean compliant,
        @Desc("Every non-compliant action (0-based index) with the reason; empty when compliant") List<Violation> violations,
        @Nullable @Desc("When not compliant: a short note to the coworker explaining what was rejected and why") String warning) {

    /** v0.0.18 🍊 One non-compliant action. */
    public record Violation(int actionIndex, String reason) {
    }
}
