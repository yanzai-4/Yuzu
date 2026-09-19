package ai.yuzu.external.safety;

import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.Nullable;

import java.util.List;

/** v0.0.16 🍊 Output of the safety review (gate and mask modes). */
public record SafetyVerdict(
        @Desc("Brief reasoning about the content") String reasoning,
        Verdict verdict,
        @Desc("Each violation found, briefly (empty when SAFE)") List<String> violations,
        @Desc("MASK mode only: exact passages to mask (empty otherwise)") List<Mask> masks,
        @Nullable @Desc("One polite sentence explaining the block (null when SAFE)") String userFacingReason) {

    /** v0.0.16 🍊 Overall verdict. */
    public enum Verdict { SAFE, UNSAFE }

    /** v0.0.16 🍊 One passage to mask, copied exactly from the content. */
    public record Mask(@Desc("The passage copied exactly from the content") String quote,
                       @Desc("Why it is masked") String reason) {
    }

    /** v0.0.16 🍊 True when the content may pass unchanged. */
    public boolean safe() {
        return verdict == Verdict.SAFE;
    }
}
