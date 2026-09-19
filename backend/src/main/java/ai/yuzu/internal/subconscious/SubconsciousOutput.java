package ai.yuzu.internal.subconscious;

import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.Nullable;

import java.util.List;

/**
 * v0.0.28 🍊 Output of one subconscious pass: a nudge, what is worth learning or remembering, conflict updates.
 *
 * <p>The advice is written in the first person because the main consciousness reads it as one of its own
 * thoughts (code renders SELF and SUBCONSCIOUS identically). Most passes return nothing but reasoning.</p>
 */
public record SubconsciousOutput(
        @Desc("Brief reasoning about what just happened; one or two sentences") String reasoning,
        @Nullable @Desc("A short first-person nudge to myself about what just happened, written as my own thought (no 'you'); null when I have nothing worth adding, which is most of the time") String advice,
        @Desc("Ways of working worth keeping for good; 0 to 2 items, empty unless this round really taught me a technique") List<Habit> learn,
        @Desc("Facts worth remembering for a long time; 0 to 2 items, empty unless this round told me something lasting") List<Fact> remember,
        @Desc("Updates to the unresolved conflicts listed for me; empty when this round said nothing about them") List<ConflictUpdate> conflictUpdates) {

    /** v0.0.28 🍊 How the agent should work in a recurring situation (goes to habit memory). */
    public record Habit(
            @Desc("Short name of the habit, for example 'Cite every source'") String name,
            @Desc("When this applies, starting with 'when ...'") String scenario,
            @Desc("Exactly what to do, concrete enough to follow without more thinking") String technique) {
    }

    /** v0.0.28 🍊 A lasting fact (goes to deep memory). */
    public record Fact(
            @Desc("Short title of the fact") String title,
            @Desc("The fact itself, self-contained: it must still make sense weeks from now") String content,
            @Desc("Up to 5 search keywords (names, topics) that someone would search for later") List<String> keywords) {
    }

    /** v0.0.28 🍊 What this round says about a conflict that was waiting for evidence. */
    public record ConflictUpdate(
            @Desc("The conflict id, copied exactly from the list of unresolved conflicts") String conflictId,
            @Desc("KEEP_OLD when what I remembered turned out right; USE_NEW when the newer version is right; MERGE when both are partly right") Resolution resolution,
            @Nullable @Desc("MERGE only: the complete text that combines both versions; null otherwise") String mergedText,
            @Desc("What in this round settled it, in one sentence") String reason) {
    }

    /** v0.0.28 🍊 How a conflict was settled. */
    public enum Resolution { KEEP_OLD, USE_NEW, MERGE }
}
