package ai.yuzu.internal.memory;

import java.time.Instant;

/**
 * v0.0.13 🍊 One working-memory entry: a copy of one input or output of the main consciousness.
 *
 * @param direction IN (something the main consciousness received) or OUT (what it decided)
 * @param source    who/what it came from, as the main consciousness sees it ("me (my own thought)", attribution)
 * @param originRef pool message id (IN) or run id (OUT); unique per agent, which makes recording idempotent
 * @param compacted true once folded into the digest (kept for time-range recall, excluded from prompts)
 */
public record WorkingMemoryEntry(String id, String agentId, long seq, String runId, Direction direction, String source,
                                 String originRef, String text, int tokens, boolean compacted, Instant createdAt) {

    /** v0.0.13 🍊 Entry direction. */
    public enum Direction { IN, OUT }
}
