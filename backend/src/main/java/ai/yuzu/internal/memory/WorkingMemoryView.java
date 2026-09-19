package ai.yuzu.internal.memory;

import java.util.List;

/**
 * v0.0.13 🍊 Working memory as shown in the UI (contract type {@code WorkingMemoryView}).
 *
 * @param digest  rolling summary of everything older than the verbatim entries (null when none)
 * @param entries verbatim entries, oldest first
 */
public record WorkingMemoryView(String agentId, String digest, List<Entry> entries) {

    /** v0.0.13 🍊 One verbatim entry. */
    public record Entry(String id, String direction, String origin, String text, String time) {
    }
}
