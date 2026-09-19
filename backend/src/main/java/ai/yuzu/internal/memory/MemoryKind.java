package ai.yuzu.internal.memory;

/**
 * v0.0.28 🍊 The two long-term memories of an agent, written by the learning and the memory module.
 *
 * <p>HABIT ("how I work") is read by cognition; DEEP ("what I know") is read on demand by the memory-read
 * tool. Both are written through the same flow: content hash → FULLTEXT search → judge → merge / overwrite /
 * ignore / hold a conflict.</p>
 */
public enum MemoryKind {

    /** v0.0.28 🍊 Habit memory: a technique plus the situation it applies to. */
    HABIT("habit"),

    /** v0.0.28 🍊 Deep memory: a long-lived fact worth remembering. */
    DEEP("deep memory");

    private final String label;

    MemoryKind(String label) {
        this.label = label;
    }

    /** v0.0.28 🍊 Word used in prompts and tool output ("habit", "deep memory"). */
    public String label() {
        return label;
    }
}
