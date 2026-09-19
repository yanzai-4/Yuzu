package ai.yuzu.internal.memory;

import java.time.Instant;

/**
 * v0.0.28 🍊 A contradiction between what an agent already remembers and something new it just saw.
 *
 * <p>The old memory is always kept while the conflict is open. Every subconscious activation counts one round
 * down; at zero the conflict expires and the old memory stays for good, unless the subconscious resolved it
 * earlier.</p>
 *
 * @param oldCopy    the stored entry at the time of the conflict
 * @param newCopy    the candidate that contradicted it
 * @param roundsLeft subconscious activations left before the conflict expires
 * @param status     OPEN, RESOLVED or EXPIRED
 */
public record MemoryConflict(String id, MemoryKind kind, String targetId, StoredMemory oldCopy,
                             MemoryCandidate newCopy, String reason, int roundsLeft, String status,
                             Instant createdAt) {

    /** v0.0.28 🍊 One block for the subconscious prompt. */
    public String describe() {
        return "- " + id + " (" + kind.label() + ", " + roundsLeft + " round(s) left)\n"
                + "  what I remember: " + oldCopy.describe() + "\n"
                + "  what I saw instead: " + newCopy.describe() + "\n"
                + "  why they clash: " + reason;
    }
}
