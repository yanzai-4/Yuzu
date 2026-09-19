package ai.yuzu.internal.planning;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.internal.intake.Stimulus;
import ai.yuzu.realtime.ErrorReporter;
import ai.yuzu.room.RoomDirectory;
import ai.yuzu.room.RoomMember;
import ai.yuzu.task.Actor;
import ai.yuzu.task.list.TaskList;
import ai.yuzu.task.list.TaskListService;
import ai.yuzu.task.list.TaskListStatus;
import ai.yuzu.task.list.TaskListView;
import ai.yuzu.task.list.TaskOp;
import ai.yuzu.task.prompt.TaskPromptRenderer;
import ai.yuzu.task.ticket.Ticket;
import ai.yuzu.task.ticket.TicketService;
import ai.yuzu.task.ticket.TicketStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * v0.0.21 🍊 {@link TaskPlanner} implementation: runs the planning module and applies its decision in code.
 *
 * <p>One planning pass per agent at a time (a per-agent lock held across the LLM call — the only lock that is;
 * inside it only the task service's own short locks are taken, so there is no lock cycle). Returns a first-person
 * note that joins the stimulus in the pool, so the main consciousness knows how its plan changed.</p>
 */
@Service
public class TaskPlannerService implements TaskPlanner {

    private final PlanningModule module;
    private final TaskListService lists;
    private final TicketService tickets;
    private final RoomDirectory directory;
    private final ErrorReporter errors;
    private final Map<AgentId, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** v0.0.21 🍊 Injects collaborators. */
    public TaskPlannerService(PlanningModule module, TaskListService lists, TicketService tickets,
                              RoomDirectory directory, ErrorReporter errors) {
        this.module = module;
        this.lists = lists;
        this.tickets = tickets;
        this.directory = directory;
        this.errors = errors;
    }

    /** v0.0.21 🍊 Plans under the agent's planning lock; never throws (a failure becomes a note). */
    @Override
    public String plan(AgentContext ctx, Stimulus stimulus, String stimulusText) {
        ReentrantLock lock = locks.computeIfAbsent(ctx.agentId(), id -> new ReentrantLock());
        lock.lock();
        try {
            return planLocked(ctx, stimulus, stimulusText);
        } catch (YuzuException e) {
            errors.report("planning", ctx.agentId().value(), e);
            return "(My task list could not be updated: " + e.getMessage() + ")";
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.21 🍊 Module call, then CREATE / UPDATE / approval request in code. */
    private String planLocked(AgentContext ctx, Stimulus stimulus, String stimulusText) {
        String roomId = ctx.profile().roomId();
        TaskListView view = lists.current(ctx.agentId());
        List<Ticket> mine = tickets.list(roomId).stream()
                .filter(t -> ctx.agentId().value().equals(t.assigneeId()))
                .filter(t -> t.status() == TicketStatus.ASSIGNED || t.status() == TicketStatus.IN_PROGRESS
                        || t.status() == TicketStatus.OPEN)
                .toList();
        String ticketsText = mine.isEmpty() ? "(none)" : mine.stream()
                .map(t -> "- " + t.id() + ": " + t.title() + " (" + t.status() + ", from " + t.creatorName()
                        + (t.requesterName() == null ? "" : ", requested by " + t.requesterName()) + ")"
                        + (t.detail() == null || t.detail().isBlank() ? "" : "\n  " + t.detail().strip()))
                .collect(Collectors.joining("\n"));
        Set<String> ticketIds = mine.stream().map(Ticket::id).collect(Collectors.toSet());
        PlanningDecision d = module.run(ctx, new PlanningModule.Input(roomId, stimulus.describe(), stimulusText,
                view.current(), TaskPromptRenderer.renderCurrent(view),
                TaskPromptRenderer.renderHistory(view.recentArchived()), ticketsText, ticketIds));
        List<String> notes = new ArrayList<>();
        TaskList list = view.current();
        switch (d.mode()) {
            case CREATE -> {
                Actor publisher = d.publisher() == null ? null : directory.byName(roomId, d.publisher().strip())
                        .map(TaskPlannerService::actor).orElse(null);
                list = lists.create(ctx.agentId(), d.goal().strip(), publisher, d.ticketId(), d.items());
                notes.add("I started a new task list: \"" + list.goal() + "\" with " + list.items().size()
                        + " steps (" + list.publisherName() + " will approve it when it is done).");
            }
            case UPDATE -> {
                if (!d.ops().isEmpty()) {
                    List<String> problems = new ArrayList<>();
                    List<TaskOp> ops = new ArrayList<>();
                    for (PlanningDecision.Op op : d.ops()) {
                        PlanningModule.toTaskOp(list, op, problems).ifPresent(ops::add);
                    }
                    list = lists.apply(ctx.agentId(), list.id(), ops);
                    notes.add("I updated my task list: " + ops.stream().map(TaskOp::summary)
                            .map(s -> s.replaceAll("item-[0-9a-f]{4}-[0-9a-f]{10}", "an item"))
                            .collect(Collectors.joining("; ")) + ".");
                }
            }
            case NONE -> {
            }
        }
        if (d.requestApproval() && list != null && list.status() == TaskListStatus.ACTIVE) {
            list = lists.requestApproval(ctx.agentId());
            notes.add("Every step is finished, so my task list now waits for " + list.publisherName()
                    + " to approve it.");
        }
        return notes.isEmpty() ? null : String.join(" ", notes);
    }

    /** v0.0.21 🍊 Room member → task actor. */
    private static Actor actor(RoomMember m) {
        return m.isAgent() ? Actor.agent(ai.yuzu.common.id.AgentId.of(m.id()), m.name()) : Actor.human(m.id(), m.name());
    }
}
