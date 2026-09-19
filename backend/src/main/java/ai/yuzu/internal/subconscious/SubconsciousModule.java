package ai.yuzu.internal.subconscious;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.internal.consciousness.PoolMessage;
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
 * v0.0.28 🍊 The subconscious (DEFAULT tier): one quiet pass over the messages of THIS round only.
 *
 * <p>It sees the new pool messages, working memory (explicitly marked as already known, so it does not learn
 * the same thing twice), the task list, the roster, its own profile, the unresolved conflicts and the current
 * time. It never sees older pool rounds, which is what keeps it cheap and keeps its advice about the here and
 * now. Its advice enters the pool as the agent's own thought and can never start a main run by itself.</p>
 */
@Component
public class SubconsciousModule extends AiModule<SubconsciousModule.Input, SubconsciousOutput> {

    /**
     * v0.0.28 🍊 One round.
     *
     * @param newMessages only the messages that arrived in this round
     * @param conflicts   unresolved memory conflicts waiting for evidence
     */
    public record Input(List<PoolMessage> newMessages, String taskState, String conflicts) {
    }

    private static final ModuleSpec<SubconsciousOutput> SPEC = new ModuleSpec<>("SUBCONSCIOUS", "Mulling it over",
            ModelTier.DEFAULT, "subconscious", SubconsciousOutput.class, false);

    private final ContextAssembler context;

    /** v0.0.28 🍊 Injects dependencies. */
    public SubconsciousModule(ModuleDeps deps, ContextAssembler context) {
        super(deps);
        this.context = context;
    }

    /** v0.0.28 🍊 Static description. */
    @Override
    protected ModuleSpec<SubconsciousOutput> spec() {
        return SPEC;
    }

    /** v0.0.28 🍊 S2 roster, S3 self, S4 tasks + conflicts, S5 working memory (known), S7 this round's messages. */
    @Override
    protected void compose(AgentContext ctx, Input input, PromptBuilder prompt) {
        prompt.add(SegmentRank.S2_ROSTER, context.roster(ctx.roomId()))
                .add(SegmentRank.S3_SELF, context.self(ctx.profile()))
                .add(SegmentRank.S4_SLOW_STATE, "Task state and open questions",
                        "My task list:\n" + input.taskState()
                                + "\n\nContradictions I have not settled yet:\n" + input.conflicts())
                .add(SegmentRank.S5_WORKING_MEMORY, "What I already know (do not learn any of this again)",
                        context.workingMemory(ctx.agentId()))
                .add(SegmentRank.S7_STIMULUS, "What just reached my mind this round", context.pool(input.newMessages()));
    }

    /** v0.0.28 🍊 Monitor text. */
    @Override
    protected String startText(Input input) {
        return "Mulling over " + input.newMessages().size() + " new thing(s)";
    }

    /** v0.0.28 🍊 Monitor text. */
    @Override
    protected String endText(SubconsciousOutput output) {
        if (output.advice() == null && output.learn().isEmpty() && output.remember().isEmpty()) {
            return "Nothing to add";
        }
        return (output.advice() == null ? "Quiet" : "A hunch") + ", " + output.learn().size() + " habit(s), "
                + output.remember().size() + " memory(s)";
    }

    /** v0.0.28 🍊 Candidates must be substantial, and a settled conflict must name one that was listed. */
    @Override
    protected List<String> semanticErrors(AgentContext ctx, Input input, SubconsciousOutput output) {
        List<String> errors = new ArrayList<>();
        for (SubconsciousOutput.Habit habit : output.learn()) {
            if (habit.name() == null || habit.name().isBlank() || habit.technique() == null
                    || habit.technique().isBlank()) {
                errors.add("every learn item needs a name and a technique.");
                break;
            }
        }
        for (SubconsciousOutput.Fact fact : output.remember()) {
            if (fact.title() == null || fact.title().isBlank() || fact.content() == null
                    || fact.content().isBlank()) {
                errors.add("every remember item needs a title and content.");
                break;
            }
        }
        for (SubconsciousOutput.ConflictUpdate update : output.conflictUpdates()) {
            if (update.conflictId() == null || !input.conflicts().contains(update.conflictId())) {
                errors.add("conflictUpdates may only mention conflict ids from the list I was given.");
                break;
            }
        }
        if (output.learn().size() > 2 || output.remember().size() > 2) {
            errors.add("at most 2 learn items and 2 remember items per round.");
        }
        return errors;
    }

    /** v0.0.28 🍊 A subconscious that cannot think stays silent (it must never disturb the main consciousness). */
    @Override
    protected Optional<SubconsciousOutput> degrade(AgentContext ctx, Input input, YuzuException error) {
        return Optional.of(new SubconsciousOutput("subconscious unavailable (" + error.code() + ")", null,
                List.of(), List.of(), List.of()));
    }
}
