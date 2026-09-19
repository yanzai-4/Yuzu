package ai.yuzu.external.safety;

import java.util.List;

/** v0.0.16 🍊 Applies the safety review's masks: each exact quote is replaced by "[masked: reason]". */
public final class ContentMasker {

    private ContentMasker() {
    }

    /** v0.0.16 🍊 Masks every quoted passage (quotes that do not occur are ignored). */
    public static String apply(String content, List<SafetyVerdict.Mask> masks) {
        String result = content;
        for (SafetyVerdict.Mask mask : masks) {
            if (mask.quote() != null && !mask.quote().isEmpty()) {
                result = result.replace(mask.quote(), "[masked: " + mask.reason() + "]");
            }
        }
        return result;
    }
}
