package ai.yuzu.external.safety;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.llm.ModelTier;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.module.ModuleSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** v0.0.16 🍊 Inbound gate: blocks unsafe content before it enters the agent's mind. */
@Component
public class SafetyGateModule extends SafetyReviewModule {

    private static final ModuleSpec<SafetyVerdict> SPEC = new ModuleSpec<>("SAFETY", "Safety check (incoming)",
            ModelTier.DEFAULT, "safety_gate", SafetyVerdict.class, true);

    /** v0.0.16 🍊 Receives dependencies. */
    public SafetyGateModule(ModuleDeps deps) {
        super(deps);
    }

    /** v0.0.16 🍊 Static description. */
    @Override
    protected ModuleSpec<SafetyVerdict> spec() {
        return SPEC;
    }

    /** v0.0.16 🍊 Gate mode never masks; a block needs a user-facing reason. */
    @Override
    protected List<String> semanticErrors(AgentContext ctx, Input input, SafetyVerdict output) {
        ArrayList<String> errors = new ArrayList<>(super.semanticErrors(ctx, input, output));
        if (!output.masks().isEmpty()) {
            errors.add("masks must be an empty list in gate mode.");
        }
        if (!output.safe() && (output.userFacingReason() == null || output.userFacingReason().isBlank())) {
            errors.add("verdict is UNSAFE but userFacingReason is empty.");
        }
        return errors;
    }

    /** v0.0.16 🍊 Monitor text. */
    @Override
    protected String endText(SafetyVerdict output) {
        return output.safe() ? "Looks safe" : "Blocked: " + String.join("; ", output.violations());
    }
}
