package ai.yuzu.internal.consciousness;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.llm.ModelTier;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.prompt.SegmentRank;
import ai.yuzu.module.AiModule;
import ai.yuzu.module.ContextAssembler;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.module.ModuleSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * v0.0.17 🍊 The main consciousness (IMPORTANT tier): ACT / THINK / END over everything in the pool.
 *
 * <p>Sees the whole batch (rendered with code-chosen sources, so its own and subconscious thoughts both read
 * "me (my own thought)"), working memory, task list, profile and permissions, the roster, its permitted tools,
 * the actions in progress and the exact current time (always the last line).</p>
 */
@Component
public class MainConsciousnessModule extends AiModule<MainConsciousnessModule.Input, MainDecision> {

    /** v0.0.17 🍊 Streak length after which the prompt asks for a decision. */
    static final int DECIDE_NOW_AFTER = 6;

    /**
     * v0.0.17 🍊 Input of one step.
     *
     * @param tools       permitted tools
     * @param inProgress  actions already being reviewed or executed
     * @param thinkStreak consecutive THINK steps before this one
     */
    public record Input(List<PoolMessage> batch, String taskState, String tools, String inProgress, int thinkStreak) {
    }

    private static final ModuleSpec<MainDecision> SPEC = new ModuleSpec<>("MAIN", "Thinking",
            ModelTier.IMPORTANT, "main", MainDecision.class, false);

    private final ContextAssembler context;

    /** v0.0.17 🍊 Injects dependencies. */
    public MainConsciousnessModule(ModuleDeps deps, ContextAssembler context) {
        super(deps);
        this.context = context;
    }

    /** v0.0.17 🍊 Static description. */
    @Override
    protected ModuleSpec<MainDecision> spec() {
        return SPEC;
    }

    /** v0.0.17 🍊 S2 roster, S3 self + tools, S4 tasks + in-progress, S5 working memory, S7 pool batch. */
    @Override
    protected void compose(AgentContext ctx, Input input, PromptBuilder prompt) {
        prompt.add(SegmentRank.S2_ROSTER, context.roster(ctx.roomId()))
                .add(SegmentRank.S3_SELF, context.self(ctx.profile()) + "\n\n## Your tools\n" + input.tools())
                .add(SegmentRank.S4_SLOW_STATE, "Your task list", input.taskState())
                .add(SegmentRank.S5_WORKING_MEMORY, context.workingMemory(ctx.agentId()));
        String stimulus = context.pool(input.batch());
        stimulus += "\n\nActions in progress: " + input.inProgress();
        if (input.thinkStreak() >= DECIDE_NOW_AFTER) {
            stimulus += "\n\nYou have been thinking for " + input.thinkStreak() + " steps in a row: decide now (ACT or END).";
        }
        prompt.add(SegmentRank.S7_STIMULUS, stimulus);
    }

    /** v0.0.17 🍊 Monitor text. */
    @Override
    protected String startText(Input input) {
        return "Thinking about " + input.batch().size() + " new thing(s)";
    }

    /** v0.0.17 🍊 Monitor text. */
    @Override
    protected String endText(MainDecision output) {
        return switch (output.mode()) {
            case ACT -> "Decided " + output.actions().size() + " action(s)";
            case THINK -> "Thinking further";
            case END -> "Done for now";
        };
    }

    /** v0.0.17 🍊 ACT needs actions, THINK needs a next thought, END has no actions. */
    @Override
    protected List<String> semanticErrors(AgentContext ctx, Input input, MainDecision output) {
        List<String> errors = new ArrayList<>();
        switch (output.mode()) {
            case ACT -> {
                if (output.actions().isEmpty() || output.actions().stream().anyMatch(a -> a == null || a.isBlank())) {
                    errors.add("mode is ACT but actions is empty or contains blank items.");
                }
                if (output.actions().size() > 8) {
                    errors.add("at most 8 actions per step.");
                }
            }
            case THINK -> {
                if (output.nextThought() == null || output.nextThought().isBlank()) {
                    errors.add("mode is THINK but nextThought is empty.");
                }
            }
            case END -> {
                if (!output.actions().isEmpty()) {
                    errors.add("mode is END but actions is not empty: choose ACT or remove the actions.");
                }
            }
        }
        return errors;
    }

    /** v0.0.17 🍊 When thinking fails, end the step (the failure is recorded in working memory by the service). */
    @Override
    protected Optional<MainDecision> degrade(AgentContext ctx, Input input, YuzuException error) {
        return Optional.of(new MainDecision("I could not think clearly (" + error.code() + "); I will wait for new input.",
                MainDecision.Mode.END, List.of(), null));
    }
}
