package ai.yuzu.internal.consciousness;

import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.Nullable;

import java.util.List;

/** v0.0.17 🍊 Output of one main-consciousness step. */
public record MainDecision(
        @Desc("Short first-person thought: what you understand and why you choose this mode") String thought,
        Mode mode,
        @Desc("ACT only: concrete actions in natural language, one per item (empty otherwise)") List<String> actions,
        @Nullable @Desc("THINK only: the next direction of your thinking") String nextThought) {

    /** v0.0.17 🍊 What the main consciousness decided to do. */
    public enum Mode { ACT, THINK, END }
}
