package ai.yuzu.external.behavior;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.llm.ModelTier;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.prompt.SegmentRank;
import ai.yuzu.module.AiModule;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.module.ModuleSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * v0.0.18 🍊 Second, independent review (IMPORTANT tier) of high-risk tool calls (e-mail, trading, code execution).
 */
@Component
public class HighRiskReviewModule extends AiModule<HighRiskReviewModule.Input, HighRiskVerdict> {

    /** v0.0.18 🍊 The original actions, the concrete high-risk calls and the agent's task list. */
    public record Input(String actions, String calls, String taskState) {
    }

    private static final ModuleSpec<HighRiskVerdict> SPEC = new ModuleSpec<>("HIGH_RISK", "Double-checking a risky step",
            ModelTier.IMPORTANT, "high_risk", HighRiskVerdict.class, false);

    /** v0.0.18 🍊 Receives dependencies. */
    public HighRiskReviewModule(ModuleDeps deps) {
        super(deps);
    }

    /** v0.0.18 🍊 Static description. */
    @Override
    protected ModuleSpec<HighRiskVerdict> spec() {
        return SPEC;
    }

    /** v0.0.18 🍊 S3 profile + scope, S4 task list, S7 actions and calls. */
    @Override
    protected void compose(AgentContext ctx, Input input, PromptBuilder prompt) {
        prompt.add(SegmentRank.S3_SELF, ctx.profile().describe())
                .add(SegmentRank.S4_SLOW_STATE, "Task list", input.taskState())
                .add(SegmentRank.S7_STIMULUS, "Actions and the high-risk tool calls",
                        "Actions:\n" + input.actions() + "\n\nHigh-risk calls:\n" + input.calls());
    }

    /** v0.0.18 🍊 Fail closed. */
    @Override
    protected Optional<HighRiskVerdict> degrade(AgentContext ctx, Input input, YuzuException error) {
        return Optional.of(new HighRiskVerdict("unavailable", false, true, List.of("high-risk review unavailable")));
    }

    /** v0.0.18 🍊 Monitor text. */
    @Override
    protected String endText(HighRiskVerdict output) {
        return output.approve() ? (output.needsHuman() ? "Needs a human's OK" : "Approved") : "Rejected";
    }
}
