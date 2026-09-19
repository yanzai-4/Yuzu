package ai.yuzu.internal.planning;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.llm.ModelTier;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.prompt.SegmentRank;
import ai.yuzu.module.AiModule;
import ai.yuzu.module.ContextAssembler;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.module.ModuleSpec;
import ai.yuzu.room.RoomDirectory;
import ai.yuzu.task.list.TaskList;
import ai.yuzu.task.list.TaskListService;
import ai.yuzu.task.list.TaskOp;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * v0.0.21 🍊 The planning module (DEFAULT tier): keeps the agent's ONE current task list in step with new input.
 *
 * <p>Independent prompt (no habit content: cognition runs separately in parallel, so neither pollutes the other's
 * context). Sees the input, working memory, the current list, the last 3 archived lists, tickets assigned to me,
 * my profile and everyone's profile and role. Code validates every operation with a dry run before anything is
 * written; the module can never archive a list (only its publisher or a human can approve).</p>
 */
@Component
public class PlanningModule extends AiModule<PlanningModule.Input, PlanningDecision> {

    static final int MAX_ITEMS = 12;

    /** v0.0.21 🍊 What the planner sees. */
    public record Input(String roomId, String stimulusKind, String stimulusText, TaskList current, String currentText,
                        String historyText, String ticketsText, Set<String> ticketIds) {
    }

    private static final ModuleSpec<PlanningDecision> SPEC = new ModuleSpec<>("PLANNING", "Planning my work",
            ModelTier.DEFAULT, "planning", PlanningDecision.class, false);

    private final ContextAssembler context;
    private final TaskListService lists;
    private final RoomDirectory directory;

    /** v0.0.21 🍊 Receives dependencies. */
    public PlanningModule(ModuleDeps deps, ContextAssembler context, TaskListService lists, RoomDirectory directory) {
        super(deps);
        this.context = context;
        this.lists = lists;
        this.directory = directory;
    }

    /** v0.0.21 🍊 Static description. */
    @Override
    protected ModuleSpec<PlanningDecision> spec() {
        return SPEC;
    }

    /** v0.0.21 🍊 S2 roster, S3 self, S4 tasks and tickets, S5 working memory, S7 the input. */
    @Override
    protected void compose(AgentContext ctx, Input input, PromptBuilder prompt) {
        prompt.add(SegmentRank.S2_ROSTER, "Everyone in the workgroup", context.roster(input.roomId()))
                .add(SegmentRank.S3_SELF, "About me", context.self(ctx.profile()))
                .add(SegmentRank.S4_SLOW_STATE, "My recent archived task lists", input.historyText())
                .add(SegmentRank.S4_SLOW_STATE, "Tickets assigned to me", input.ticketsText())
                .add(SegmentRank.S4_SLOW_STATE, "My current task list", input.currentText())
                .add(SegmentRank.S5_WORKING_MEMORY, "My working memory", context.workingMemory(ctx.agentId()))
                .add(SegmentRank.S7_STIMULUS, "New input (" + input.stimulusKind() + ")", input.stimulusText());
    }

    /** v0.0.21 🍊 Mode-specific rules, member names, ticket ids, item numbers and a dry run of the operations. */
    @Override
    protected List<String> semanticErrors(AgentContext ctx, Input input, PlanningDecision out) {
        List<String> errors = new ArrayList<>();
        switch (out.mode()) {
            case CREATE -> {
                if (input.current() != null) {
                    errors.add("I already have a current task list; use UPDATE (or NONE) instead of CREATE.");
                }
                if (out.goal() == null || out.goal().isBlank()) {
                    errors.add("CREATE needs a goal.");
                }
                if (out.items() == null || out.items().isEmpty() || out.items().size() > MAX_ITEMS) {
                    errors.add("CREATE needs 1 to " + MAX_ITEMS + " items.");
                }
                if (out.ticketId() != null && !input.ticketIds().contains(out.ticketId())) {
                    errors.add("ticketId " + out.ticketId() + " is not one of the tickets assigned to me.");
                }
                if (out.publisher() == null && out.ticketId() == null) {
                    errors.add("CREATE needs the publisher's name (or the ticket id).");
                }
                if (out.publisher() != null && directory.byName(input.roomId(), out.publisher().strip()).isEmpty()) {
                    errors.add("publisher \"" + out.publisher() + "\" is not a member of the workgroup; use an exact name from the roster.");
                }
            }
            case UPDATE -> {
                if (input.current() == null) {
                    errors.add("I have no current task list; use CREATE (or NONE).");
                } else if (out.ops().isEmpty() && !out.requestApproval()) {
                    errors.add("UPDATE needs at least one operation.");
                } else if (!out.ops().isEmpty()) {
                    List<TaskOp> ops = new ArrayList<>();
                    for (PlanningDecision.Op op : out.ops()) {
                        toTaskOp(input.current(), op, errors).ifPresent(ops::add);
                    }
                    if (errors.isEmpty()) {
                        errors.addAll(lists.validate(ctx.agentId(), ops));
                    }
                }
            }
            case NONE -> {
                if (out.requestApproval() && input.current() == null) {
                    errors.add("requestApproval is true but I have no current task list.");
                }
            }
        }
        return errors;
    }

    /** v0.0.21 🍊 Degrades to "no change" (the list stays as it is). */
    @Override
    protected java.util.Optional<PlanningDecision> degrade(AgentContext ctx, Input input, YuzuException error) {
        return java.util.Optional.of(new PlanningDecision("planning unavailable: " + error.code(),
                PlanningDecision.Mode.NONE, null, List.of(), null, null, List.of(), false));
    }

    /** v0.0.21 🍊 Monitor text. */
    @Override
    protected String endText(PlanningDecision output) {
        return switch (output.mode()) {
            case NONE -> output.requestApproval() ? "Asking for approval" : "Plan unchanged";
            case CREATE -> "New task list";
            case UPDATE -> "Updated my tasks";
        };
    }

    /** v0.0.21 🍊 Converts a numbered operation to a task operation on item ids (errors collected). */
    static Optional<TaskOp> toTaskOp(TaskList list, PlanningDecision.Op op, List<String> errors) {
        return switch (op.op()) {
            case ADD -> blank(op.text()) ? fail(errors, "ADD needs the item text in \"text\".") : Optional.of(TaskOp.add(op.text().strip()));
            case EDIT_GOAL -> blank(op.goal()) || blank(op.text()) ? fail(errors, "EDIT_GOAL needs \"goal\" and the reason in \"text\".")
                    : Optional.of(TaskOp.editGoal(op.goal().strip(), op.text().strip()));
            default -> {
                if (op.item() == null || list.itemAt(op.item()).isEmpty()) {
                    yield fail(errors, op.op() + " needs an existing item number; item " + op.item() + " does not exist.");
                }
                String id = list.itemAt(op.item()).orElseThrow().id();
                yield switch (op.op()) {
                    case START -> Optional.of(TaskOp.start(id));
                    case CHECK -> Optional.of(TaskOp.check(id, blank(op.text()) ? null : op.text().strip()));
                    case STRIKE -> blank(op.text()) ? fail(errors, "STRIKE needs the reason in \"text\".")
                            : Optional.of(TaskOp.strike(id, op.text().strip()));
                    default -> blank(op.text()) ? fail(errors, "NOTE needs the note in \"text\".")
                            : Optional.of(TaskOp.note(id, op.text().strip()));
                };
            }
        };
    }

    /** v0.0.21 🍊 Records an error and yields nothing. */
    private static Optional<TaskOp> fail(List<String> errors, String message) {
        errors.add(message);
        return Optional.empty();
    }

    /** v0.0.21 🍊 Null or blank. */
    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
