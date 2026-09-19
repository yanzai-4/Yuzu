package ai.yuzu.internal.memory;

import java.time.Instant;

/**
 * v0.0.28 🍊 An entry that is already in a long-term memory (what the judge compares a candidate against).
 *
 * @param scenario HABIT only: when the habit applies ("" for deep memories)
 * @param body     habit technique / deep-memory content
 */
public record StoredMemory(String id, MemoryKind kind, String title, String scenario, String body,
                           Instant createdAt) {

    /** v0.0.28 🍊 One line for the judge prompt and the conflict list. */
    public String describe() {
        return kind == MemoryKind.HABIT
                ? title + " — when: " + scenario + " — technique: " + body
                : title + " — " + body;
    }
}
