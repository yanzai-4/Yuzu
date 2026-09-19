package ai.yuzu.internal.memory;

import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.Nullable;

/** v0.0.28 🍊 Output of the memory judge: how a candidate relates to the similar entries already remembered. */
public record MemoryJudgement(
        @Desc("Brief reasoning: how does the candidate relate to what is already remembered?") String reasoning,
        @Desc("NEW: nothing remembered covers it; DUPLICATE: an existing entry already covers it (possibly partly); CONFLICT: an existing entry says the opposite") Verdict verdict,
        @Nullable @Desc("DUPLICATE and CONFLICT: the id of the existing entry, copied exactly from the list; null for NEW") String targetId,
        @Desc("DUPLICATE only: MERGE to fold the candidate into the existing entry, OVERWRITE when the candidate simply replaces it, IGNORE when it adds nothing. Use IGNORE for NEW and CONFLICT") Action action,
        @Nullable @Desc("MERGE only: the complete replacement text of the existing entry, combining both; null otherwise") String mergedText,
        @Nullable @Desc("CONFLICT only: exactly what contradicts what, in one sentence; null otherwise") String conflictReason) {

    /** v0.0.28 🍊 How the candidate relates to what is already remembered. */
    public enum Verdict { NEW, DUPLICATE, CONFLICT }

    /** v0.0.28 🍊 What to do with a duplicate. */
    public enum Action { MERGE, OVERWRITE, IGNORE }
}
