package ai.yuzu.task.list;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.Permission;
import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.ConflictException;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import ai.yuzu.task.Actor;
import ai.yuzu.task.ActorResolver;
import ai.yuzu.task.TaskText;
import ai.yuzu.task.ticket.Ticket;
import ai.yuzu.task.ticket.TicketLinkTarget;
import ai.yuzu.task.ticket.TicketService;
import ai.yuzu.task.ticket.TicketStatus;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** v0.0.10 🍊 Every agent's ONE current task list: create, apply planning ops, request approval, approve and archive. */
@Service
public class TaskListService implements TicketLinkTarget {

    /** v0.0.10 🍊 Maximum number of operations accepted by one apply call. */
    public static final int MAX_OPS = 50;
    /** v0.0.10 🍊 Archived lists carried by every view ("the last 3 archived task lists and how they went"). */
    public static final int RECENT_ARCHIVED = TaskListStore.RECENT_ARCHIVED;
    private static final int MAX_ARCHIVED_QUERY = 20;
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);

    // Concurrency: every write for an agent runs under that agent's ReentrantLock and inside one transaction whose
    // list UPDATE is version-checked, so concurrent planning calls are serialized and can never overwrite each
    // other. The per-agent view is cached and replaced (after commit, still under the lock) on every change.
    private final TaskListStore store;
    private final AgentService agents;
    private final ActorResolver actors;
    private final TicketService tickets;
    private final SseHub hub;
    private final NaturalTime time;
    private final AsyncRunner runner;
    private final TransactionTemplate tx;
    private final ApprovalPolicy approvals;
    private final AgentLocks locks = new AgentLocks(LOCK_TIMEOUT);
    private final Cache<AgentId, TaskListView> views = Caffeine.newBuilder().maximumSize(1_000).build();

    /** v0.0.10 🍊 Injects collaborators. */
    TaskListService(TaskListStore store, AgentService agents, ActorResolver actors, TicketService tickets, SseHub hub,
                    NaturalTime time, AsyncRunner runner, PlatformTransactionManager transactions) {
        this.store = store;
        this.agents = agents;
        this.actors = actors;
        this.tickets = tickets;
        this.hub = hub;
        this.time = time;
        this.runner = runner;
        this.tx = new TransactionTemplate(transactions);
        this.approvals = new ApprovalPolicy(actors);
    }

    /** v0.0.10 🍊 The agent's current list and latest archived lists (cached per agent, replaced on every change). */
    public TaskListView current(AgentId agentId) {
        TaskListView cached = views.getIfPresent(agentId);
        if (cached != null) {
            return cached;
        }
        agents.require(agentId);
        return locks.withLock(agentId, () -> viewUnderLock(agentId));
    }

    /** v0.0.10 🍊 The agent's latest archived lists with their outcomes, most recent first (for planning prompts). */
    public List<TaskList> recentArchived(AgentId agentId, int limit) {
        int wanted = Math.max(1, Math.min(limit, MAX_ARCHIVED_QUERY));
        if (wanted <= RECENT_ARCHIVED) {
            List<TaskList> recent = current(agentId).recentArchived();
            return List.copyOf(recent.subList(0, Math.min(wanted, recent.size())));
        }
        agents.require(agentId);
        return tx.execute(status -> store.loadArchived(agentId, wanted));
    }

    /** v0.0.10 🍊 Creates the agent's current list; CONFLICT while another list is ACTIVE or AWAITING_APPROVAL. */
    public TaskList create(AgentId agentId, String goal, Actor publisher, String ticketId, List<String> items) {
        AgentProfile owner = agents.require(agentId);
        if (!owner.isPresent()) {
            throw new ConflictException(owner.name() + " is retired and cannot take new work.")
                    .forAgent(agentId.value());
        }
        String cleanGoal = TaskText.required(goal, "The goal", TaskListDraft.MAX_GOAL);
        Ticket ticket = ticketId == null ? null : workableTicket(owner, ticketId);
        Actor verified = actors.verify(choosePublisher(owner, publisher, ticket), owner.roomId());
        actors.require(verified, Permission.TASK_ASSIGN, "publish work to coworkers");
        Instant now = time.nowInstant();
        TaskListDraft draft = TaskListDraft.forNewList(
                TaskListRow.fresh(agentId, cleanGoal, verified, ticket == null ? null : ticket.id(), now), items, now);
        draft.throwIfRejected();
        return locks.withLock(agentId, () -> {
            store.findOpen(agentId).ifPresent(open -> {
                throw new ConflictException(owner.name() + " already has a current task list (" + open.id() + ", "
                        + open.status() + "); it must be approved and archived before another one starts.")
                        .with("listId", open.id()).forAgent(agentId.value());
            });
            TaskList created = commit(owner, () -> {
                store.insert(draft);
                return store.loadView(agentId);
            }).current();
            if (ticket != null) {
                syncTicket(agentId, ticket.id(), created.id(), TicketStatus.IN_PROGRESS);
            }
            return created;
        });
    }

    /** v0.0.10 🍊 Applies planning operations to the current list, all or nothing; returns the updated list. */
    public TaskList apply(AgentId agentId, List<TaskOp> ops) {
        return apply(agentId, null, ops);
    }

    /** v0.0.10 🍊 Like {@link #apply(AgentId, List)}, but CONFLICT unless {@code expectedListId} is still current. */
    public TaskList apply(AgentId agentId, String expectedListId, List<TaskOp> ops) {
        requireOps(ops);
        AgentProfile owner = agents.require(agentId);
        return locks.withLock(agentId, () -> {
            TaskListRow list = requireOpen(agentId, expectedListId);
            TaskListDraft draft = TaskListDraft.of(list, store.itemsOf(agentId, list.id()), time.nowInstant());
            draft.applyAll(ops);
            draft.throwIfRejected();
            if (!draft.changed()) {
                return viewUnderLock(agentId).current();
            }
            TaskList updated = commit(owner, () -> {
                store.save(draft);
                return store.loadView(agentId);
            }).current();
            if (draft.reopened() && list.ticketId() != null) {
                syncTicket(agentId, list.ticketId(), list.id(), TicketStatus.IN_PROGRESS);
            }
            return updated;
        });
    }

    /** v0.0.10 🍊 Dry-runs operations on the current list without writing; returns every problem (empty = valid). */
    public List<String> validate(AgentId agentId, List<TaskOp> ops) {
        agents.require(agentId);
        if (ops == null || ops.isEmpty()) {
            return List.of("No task operations were given.");
        }
        if (ops.size() > MAX_OPS) {
            return List.of("At most " + MAX_OPS + " task operations can be applied at once.");
        }
        Optional<TaskListRow> open = store.findOpen(agentId);
        if (open.isEmpty()) {
            return List.of("There is no current task list; create one first.");
        }
        TaskListDraft draft = TaskListDraft.of(open.get(), store.itemsOf(agentId, open.get().id()), time.nowInstant());
        draft.applyAll(ops);
        return draft.problemMessages();
    }

    /** v0.0.10 🍊 Moves the current list to AWAITING_APPROVAL once every item is DONE or STRUCK (idempotent). */
    public TaskList requestApproval(AgentId agentId) {
        AgentProfile owner = agents.require(agentId);
        return locks.withLock(agentId, () -> {
            TaskListRow list = requireOpen(agentId, null);
            if (list.status() == TaskListStatus.AWAITING_APPROVAL) {
                return viewUnderLock(agentId).current();
            }
            List<TaskItemRow> items = store.itemsOf(agentId, list.id());
            if (items.isEmpty()) {
                throw new ConflictException("Task list " + list.id() + " has no items yet; add and finish items "
                        + "before asking for approval.").with("listId", list.id()).forAgent(agentId.value());
            }
            List<Integer> unfinished = items.stream().filter(item -> !item.state().isFinished())
                    .map(TaskItemRow::ord).toList();
            if (!unfinished.isEmpty()) {
                throw new ConflictException(unfinishedMessage(unfinished)).with("listId", list.id())
                        .with("unfinishedItems", unfinished).forAgent(agentId.value());
            }
            TaskListRow next = list.awaitingApproval(time.nowInstant());
            TaskList updated = commit(owner, () -> {
                store.update(next, list.version());
                return store.loadView(agentId);
            }).current();
            if (list.ticketId() != null) {
                syncTicket(agentId, list.ticketId(), list.id(), TicketStatus.DONE);
            }
            return updated;
        });
    }

    /** v0.0.10 🍊 Archives an AWAITING_APPROVAL list approved by its publisher or any human; clears the current list. */
    public TaskListView approve(String listId, Actor approver) {
        AgentId agentId = ownerOf(listId);
        AgentProfile owner = agents.find(agentId).orElseThrow(() -> unknownList(listId));
        Actor verified = actors.verify(approver, owner.roomId());
        return locks.withLock(agentId, () -> {
            TaskListRow list = store.findById(agentId, listId).orElseThrow(() -> unknownList(listId));
            approvals.requireCanApprove(verified, list);
            if (list.status() != TaskListStatus.AWAITING_APPROVAL) {
                String why = list.status() == TaskListStatus.ARCHIVED ? "is already archived"
                        : "is still ACTIVE; every item must be DONE or STRUCK and approval requested first";
                throw new ConflictException("Task list " + listId + " " + why + ".")
                        .with("status", list.status().name()).forAgent(agentId.value());
            }
            List<TaskItemRow> items = store.itemsOf(agentId, listId);
            TaskListRow next = list.archived(verified, OutcomeWriter.summarize(items, verified), time.nowInstant());
            TaskListView view = commit(owner, () -> {
                store.update(next, list.version());
                return store.loadView(agentId);
            });
            if (list.ticketId() != null) {
                syncTicket(agentId, list.ticketId(), listId, TicketStatus.APPROVED);
            }
            return view;
        });
    }

    /** v0.0.10 🍊 List side of TicketService.linkList: records the ticket on the open list and moves the ticket. */
    @Override
    public Ticket attachTicket(AgentId assignee, String listId, String ticketId) {
        AgentProfile owner = agents.require(assignee);
        return locks.withLock(assignee, () -> {
            TaskListRow list = requireOpen(assignee, listId);
            if (list.ticketId() != null && !list.ticketId().equals(ticketId)) {
                throw new ConflictException("Task list " + listId + " already works on ticket " + list.ticketId() + ".")
                        .with("ticketId", list.ticketId()).forAgent(assignee.value());
            }
            if (list.ticketId() == null) {
                TaskListRow next = list.withTicket(ticketId, time.nowInstant());
                commit(owner, () -> {
                    store.update(next, list.version());
                    return store.loadView(assignee);
                });
            }
            TicketStatus target = list.status() == TaskListStatus.AWAITING_APPROVAL
                    ? TicketStatus.DONE : TicketStatus.IN_PROGRESS;
            return tickets.syncWithList(ticketId, listId, target).orElseGet(() -> tickets.get(ticketId));
        });
    }

    /** v0.0.10 🍊 Runs a write in a transaction, then caches and publishes the fresh view (caller holds the lock). */
    private TaskListView commit(AgentProfile owner, Supplier<TaskListView> write) {
        TaskListView view = tx.execute(status -> write.get());
        views.put(owner.agentId(), view);
        hub.publish(owner.roomId(), EventType.TASK_LIST, owner.agentId().value(), view);
        return view;
    }

    /** v0.0.10 🍊 The cached view, loaded consistently on a miss (caller holds the agent lock). */
    private TaskListView viewUnderLock(AgentId agentId) {
        TaskListView cached = views.getIfPresent(agentId);
        if (cached != null) {
            return cached;
        }
        TaskListView loaded = tx.execute(status -> store.loadView(agentId));
        views.put(agentId, loaded);
        return loaded;
    }

    /** v0.0.10 🍊 The open list, checked against the expected id; CONFLICT when none, changed, or archived. */
    private TaskListRow requireOpen(AgentId agentId, String expectedListId) {
        Optional<TaskListRow> open = store.findOpen(agentId);
        if (expectedListId != null && open.map(row -> !row.id().equals(expectedListId)).orElse(true)) {
            boolean archived = store.findById(agentId, expectedListId)
                    .map(row -> row.status() == TaskListStatus.ARCHIVED).orElse(false);
            throw new ConflictException(archived ? "Task list " + expectedListId + " is archived; archived lists never "
                    + "change." : "Task list " + expectedListId + " is not the current task list of " + agentId + ".")
                    .with("listId", expectedListId).forAgent(agentId.value());
        }
        return open.orElseThrow(() -> new ConflictException("Agent " + agentId
                + " has no current task list; create one first.").forAgent(agentId.value()));
    }

    /** v0.0.10 🍊 A ticket the agent may start a list for: same room, assigned to it, ASSIGNED/IN_PROGRESS, unlinked. */
    private Ticket workableTicket(AgentProfile owner, String ticketId) {
        Ticket ticket = tickets.get(ticketId);
        String agentId = owner.agentId().value();
        String problem = null;
        if (!ticket.roomId().equals(owner.roomId()) || !agentId.equals(ticket.assigneeId())) {
            problem = "is not assigned to " + owner.name();
        } else if (ticket.status() != TicketStatus.ASSIGNED && ticket.status() != TicketStatus.IN_PROGRESS) {
            problem = "is " + ticket.status() + "; only ASSIGNED or IN_PROGRESS tickets can get a task list";
        } else if (ticket.listId() != null) {
            problem = "already follows task list " + ticket.listId();
        }
        if (problem != null) {
            throw new ConflictException("Ticket " + ticketId + " " + problem + ".").with("ticketId", ticketId)
                    .forAgent(agentId);
        }
        return ticket;
    }

    /** v0.0.10 🍊 Explicit publisher, else whoever assigned the linked ticket (or its creator); BAD_REQUEST if none. */
    private static Actor choosePublisher(AgentProfile owner, Actor explicit, Ticket ticket) {
        if (explicit != null) {
            return explicit;
        }
        if (ticket != null) {
            return ticket.assignedBy() != null ? ticket.assignedBy() : ticket.creator();
        }
        throw new BadRequestException("A task list needs a publisher: the human or agent who assigned the work.")
                .forAgent(owner.agentId().value());
    }

    /** v0.0.10 🍊 Moves a linked ticket with its list; failures are reported (UI toast) but never undo the change. */
    private void syncTicket(AgentId agentId, String ticketId, String listId, TicketStatus target) {
        try {
            tickets.syncWithList(ticketId, listId, target);
        } catch (RuntimeException e) {
            runner.report("task-ticket-sync", agentId.value(), e);
        }
    }

    /** v0.0.10 🍊 BAD_REQUEST for an empty or oversized batch. */
    private static void requireOps(List<TaskOp> ops) {
        if (ops == null || ops.isEmpty()) {
            throw new BadRequestException("No task operations were given.");
        }
        if (ops.size() > MAX_OPS) {
            throw new BadRequestException("At most " + MAX_OPS + " task operations can be applied at once.")
                    .with("max", MAX_OPS);
        }
    }

    /** v0.0.10 🍊 "Item 2 is not finished ..." or "Items 2, 4 are not finished ...". */
    private static String unfinishedMessage(List<Integer> ords) {
        String numbers = ords.stream().map(String::valueOf).collect(Collectors.joining(", "));
        boolean one = ords.size() == 1;
        return (one ? "Item " + numbers + " is" : "Items " + numbers + " are") + " not finished; check "
                + (one ? "it off or strike it" : "them off or strike them") + " (with a reason) before asking for "
                + "approval.";
    }

    /** v0.0.10 🍊 The agent owning a list id ({@code list-<agentHex>-<10hex>}); NOT_FOUND for anything else. */
    private static AgentId ownerOf(String listId) {
        String prefix = DataName.TASK_LIST.prefix() + "-";
        if (listId == null || !IdGen.isRecordId(listId) || !listId.startsWith(prefix)) {
            throw unknownList(listId);
        }
        return AgentId.of("agent-" + listId.substring(prefix.length(), prefix.length() + 4));
    }

    /** v0.0.10 🍊 NOT_FOUND for an unknown task list. */
    private static YuzuException unknownList(String listId) {
        return new NotFoundException("Task list " + listId + " does not exist.").with("listId", listId);
    }
}
