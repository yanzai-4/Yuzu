package ai.yuzu.internal.memory;

import ai.yuzu.common.id.AgentId;

import java.util.List;

/**
 * v0.0.13 🍊 Folds the oldest working-memory entries into the rolling digest (implemented by the AI compactor).
 */
@FunctionalInterface
public interface WorkingMemoryCompactor {

    /**
     * v0.0.13 🍊 Returns the new digest that replaces {@code previousDigest} and covers {@code entries}.
     *
     * @param previousDigest current digest (empty when none)
     * @param entries        oldest verbatim entries to fold in, ascending
     */
    String compact(AgentId agentId, String previousDigest, List<WorkingMemoryEntry> entries);
}
