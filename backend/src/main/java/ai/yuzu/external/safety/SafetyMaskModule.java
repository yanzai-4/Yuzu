package ai.yuzu.external.safety;

import ai.yuzu.llm.ModelTier;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.module.ModuleSpec;
import org.springframework.stereotype.Component;

/** v0.0.16 🍊 Outbound review of tool results: masks only the problematic passages. */
@Component
public class SafetyMaskModule extends SafetyReviewModule {

    private static final ModuleSpec<SafetyVerdict> SPEC = new ModuleSpec<>("SAFETY", "Safety check (tool results)",
            ModelTier.DEFAULT, "safety_mask", SafetyVerdict.class, true);

    /** v0.0.16 🍊 Receives dependencies. */
    public SafetyMaskModule(ModuleDeps deps) {
        super(deps);
    }

    /** v0.0.16 🍊 Static description. */
    @Override
    protected ModuleSpec<SafetyVerdict> spec() {
        return SPEC;
    }

    /** v0.0.16 🍊 Monitor text. */
    @Override
    protected String endText(SafetyVerdict output) {
        return output.masks().isEmpty() ? "Results look safe" : "Masked " + output.masks().size() + " passage(s)";
    }
}
