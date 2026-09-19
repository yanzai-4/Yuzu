package ai.yuzu.internal.memory;

/**
 * v0.0.28 🍊 What happened to a candidate offered to a long-term memory.
 *
 * @param id   the entry that was written or kept (the conflict id for CONFLICT; null when nothing was touched)
 * @param note one first-person sentence, used by the learn tool and the monitor
 */
public record MemoryOutcome(Outcome outcome, String id, String note) {

    /** v0.0.28 🍊 The six possible endings of the write flow. */
    public enum Outcome { CREATED, MERGED, OVERWRITTEN, DUPLICATE, CONFLICT, IGNORED }

    /** v0.0.28 🍊 A brand new entry. */
    public static MemoryOutcome created(MemoryKind kind, String id, String title) {
        return new MemoryOutcome(Outcome.CREATED, id, "I learned a new " + kind.label() + ": \"" + title + "\".");
    }

    /** v0.0.28 🍊 Folded into an entry that already existed. */
    public static MemoryOutcome merged(MemoryKind kind, String id, String title) {
        return new MemoryOutcome(Outcome.MERGED, id,
                "I merged this into the " + kind.label() + " I already had: \"" + title + "\".");
    }

    /** v0.0.28 🍊 Replaced an entry that had become wrong. */
    public static MemoryOutcome overwritten(MemoryKind kind, String id, String title) {
        return new MemoryOutcome(Outcome.OVERWRITTEN, id,
                "I replaced my older " + kind.label() + " with this one: \"" + title + "\".");
    }

    /** v0.0.28 🍊 Already remembered word for word, or judged to add nothing. */
    public static MemoryOutcome duplicate(MemoryKind kind, String id, String title) {
        return new MemoryOutcome(Outcome.DUPLICATE, id,
                "I already remember this " + kind.label() + ": \"" + title + "\", so I kept what I had.");
    }

    /** v0.0.28 🍊 Contradicts what the agent remembers; held for a while, old entry kept. */
    public static MemoryOutcome conflict(MemoryKind kind, String conflictId, int rounds, String reason) {
        return new MemoryOutcome(Outcome.CONFLICT, conflictId, "This conflicts with a " + kind.label()
                + " I already have (" + reason + "), so I kept the old one and will watch for "
                + rounds + " more rounds before deciding.");
    }

    /** v0.0.28 🍊 Nothing was worth storing. */
    public static MemoryOutcome ignored(String why) {
        return new MemoryOutcome(Outcome.IGNORED, null, why);
    }
}
