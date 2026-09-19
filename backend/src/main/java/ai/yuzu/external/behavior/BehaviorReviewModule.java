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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * v0.0.18 🍊 Behavior review (DEFAULT tier): every action the main consciousness decides is checked before it runs.
 *
 * <p>Sees only the actions, the security guideline (handbook) and the permission scope. If ANY action is
 * non-compliant the whole batch is rejected. Fails closed.</p>
 */
@Component
public class BehaviorReviewModule extends AiModule<List<String>, BehaviorVerdict> {

    private static final ModuleSpec<BehaviorVerdict> SPEC = new ModuleSpec<>("BEHAVIOR", "Checking my plan",
            ModelTier.DEFAULT, "behavior", BehaviorVerdict.class, true);

    /** v0.0.18 🍊 Receives dependencies. */
    public BehaviorReviewModule(ModuleDeps deps) {
        super(deps);
    }

    /** v0.0.18 🍊 Static description. */
    @Override
    protected ModuleSpec<BehaviorVerdict> spec() {
        return SPEC;
    }

    /** v0.0.18 🍊 S3 permission scope, S7 numbered actions. */
    @Override
    protected void compose(AgentContext ctx, List<String> actions, PromptBuilder prompt) {
        prompt.add(SegmentRank.S3_SELF, "Permission scope of the coworker",
                        ctx.profile().name() + " — " + ctx.profile().title() + "\nWork scope: " + ctx.profile().scopeText()
                                + "\n" + ctx.profile().scope().describe())
                .add(SegmentRank.S7_STIMULUS, "Actions to review", numbered(actions));
    }

    /** v0.0.18 🍊 Rejections need valid indices and a warning. */
    @Override
    protected List<String> semanticErrors(AgentContext ctx, List<String> actions, BehaviorVerdict output) {
        List<String> errors = new ArrayList<>();
        if (!output.compliant()) {
            if (output.violations().isEmpty()) {
                errors.add("compliant is false but violations is empty.");
            }
            if (output.warning() == null || output.warning().isBlank()) {
                errors.add("compliant is false but warning is empty.");
            }
        } else if (!output.violations().isEmpty()) {
            errors.add("compliant is true but violations is not empty.");
        }
        output.violations().stream().filter(v -> v.actionIndex() < 0 || v.actionIndex() >= actions.size())
                .forEach(v -> errors.add("actionIndex " + v.actionIndex() + " does not exist."));
        return errors;
    }

    /** v0.0.18 🍊 Fail closed: reject the batch when the review cannot run. */
    @Override
    protected Optional<BehaviorVerdict> degrade(AgentContext ctx, List<String> actions, YuzuException error) {
        return Optional.of(new BehaviorVerdict("Behavior review unavailable: " + error.code(), false,
                List.of(new BehaviorVerdict.Violation(0, "the behavior review is temporarily unavailable")),
                "My behavior check is unavailable right now, so none of my planned actions were executed. I should try again shortly."));
    }

    /** v0.0.18 🍊 Monitor text. */
    @Override
    protected String endText(BehaviorVerdict output) {
        return output.compliant() ? "Plan approved" : "Plan rejected";
    }

    /** v0.0.18 🍊 "0. action" lines. */
    public static String numbered(List<String> actions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < actions.size(); i++) {
            sb.append(i).append(". ").append(actions.get(i).strip()).append('\n');
        }
        return sb.toString().strip();
    }
}
