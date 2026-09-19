package ai.yuzu.task.ticket;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
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
import ai.yuzu.room.RoomRepository;
import ai.yuzu.task.Actor;
import ai.yuzu.task.ActorResolver;
import ai.yuzu.task.TaskText;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** v0.0.20 🍊 Room-level tickets: create, assign, lifecycle moves, task-list links; publishes ticket.upsert. */
@Service
public class TicketService {

    static final int MAX_TITLE = 200;
    static final int MAX_DETAIL = 8_000;
    static final int LIST_LIMIT = 500;

    private final TicketRepository repository;
    private final ActorResolver actors;
    private final AgentService agents;
    private final RoomRepository rooms;
    private final SseHub hub;
    private final NaturalTime time;
    private final TransactionTemplate tx;
    private final ObjectProvider<TicketLinkTarget> linkTargets;
    private final TicketPolicy policy;

    /** v0.0.20 🍊 Injects collaborators; the link target (task-list service) is resolved lazily to avoid a cycle. */
    public TicketService(TicketRepository repository, ActorResolver actors, AgentService agents, RoomRepository rooms,
                         SseHub hub, NaturalTime time, PlatformTransactionManager transactions,
                         ObjectProvider<TicketLinkTarget> linkTargets) {
        this.repository = repository;
        this.actors = actors;
        this.agents = agents;
        this.rooms = rooms;
        this.hub = hub;
        this.time = time;
        this.tx = new TransactionTemplate(transactions);
        this.linkTargets = linkTargets;
        this.policy = new TicketPolicy(actors);
    }

    /** v0.0.20 🍊 Creates a ticket (humans or TASK_ASSIGN agents only), optionally assigned at once; publishes it. */
    public Ticket create(String roomId, Actor creator, NewTicket request) {
        if (request == null) {
            throw new BadRequestException("The ticket is empty.");
        }
        if (roomId == null || !rooms.exists(roomId)) {
            throw new NotFoundException("Room " + roomId + " does not exist.");
        }
        Actor author = actors.verify(creator, roomId);
        policy.requireCanManage(author, "create tickets");
        String title = TaskText.required(request.title(), "The ticket title", MAX_TITLE);
        String detail = TaskText.multiLine(request.detail(), "The ticket detail", MAX_DETAIL);
        Actor requester = request.requester() != null ? actors.verify(request.requester(), roomId)
                : author.isHuman() ? author : null;
        String sourceMessageId = request.sourceMessageId();
        if (sourceMessageId != null && !IdGen.isRecordId(sourceMessageId)) {
            throw new BadRequestException("Invalid source message id " + sourceMessageId + ".");
        }
        Instant now = time.nowInstant();
        TicketRow row = new TicketRow(roomId, null, title, detail, TicketStatus.OPEN, author, null, null,
                requester == null ? null : requester.id(), requester == null ? null : requester.name(),
                sourceMessageId, null, 0, now, now);
        if (request.assignee() != null) {
            row = row.assignedTo(requireAssignable(request.assignee(), roomId).agentId().value(), author, now);
        }
        AgentId owner = author.isAgent() ? author.agentId() : AgentId.SYSTEM;
        TicketRow saved = row.withId(repository.insert(row, owner));
        Ticket view = saved.toView(time);
        publishAfterCommit(view);
        return view;
    }

    /** v0.0.20 🍊 Assigns an OPEN or ASSIGNED ticket to a present agent of its room (humans or TASK_ASSIGN agents). */
    public Ticket assign(String ticketId, AgentId assignee, Actor actor) {
        if (assignee == null) {
            throw new BadRequestException("An assignee is required.");
        }
        TicketRow current = require(ticketId);
        Actor by = actors.verify(actor, current.roomId());
        policy.requireCanManage(by, "assign tickets");
        AgentProfile target = requireAssignable(assignee, current.roomId());
        return mutate(ticketId, row -> {
            if (row.status() != TicketStatus.OPEN && row.status() != TicketStatus.ASSIGNED) {
                throw new ConflictException("Ticket " + ticketId + " is " + row.status()
                        + "; only OPEN or ASSIGNED tickets can be assigned.").with("status", row.status().name());
            }
            if (target.agentId().value().equals(row.assigneeId())) {
                return row;
            }
            return row.assignedTo(target.agentId().value(), by, time.nowInstant());
        });
    }

    /** v0.0.20 🍊 Moves a ticket along its lifecycle with per-status permission checks; the same status is a no-op. */
    public Ticket updateStatus(String ticketId, TicketStatus status, Actor actor) {
        if (status == null) {
            throw new BadRequestException("A target status is required.");
        }
        TicketRow current = require(ticketId);
        Actor by = actors.verify(actor, current.roomId());
        return mutate(ticketId, row -> {
            if (row.status() == status) {
                return row;
            }
            policy.requireCanMove(by, row, status);
            if (!row.status().canMoveTo(status)) {
                String hint = status == TicketStatus.ASSIGNED ? " Use assign to give it to a coworker." : "";
                throw new ConflictException("Ticket " + ticketId + " cannot move from " + row.status() + " to "
                        + status + "." + hint).with("from", row.status().name()).with("to", status.name());
            }
            if (status == TicketStatus.APPROVED && row.listId() != null) {
                throw new ConflictException("Ticket " + ticketId + " follows task list " + row.listId()
                        + "; approve that task list instead.").with("listId", row.listId());
            }
            return row.withStatus(status, time.nowInstant());
        });
    }

    /** v0.0.20 🍊 Links the ticket to its assignee's open task list (both sides); ASSIGNED becomes IN_PROGRESS. */
    public Ticket linkList(String ticketId, String listId, Actor actor) {
        TicketRow current = require(ticketId);
        Actor by = actors.verify(actor, current.roomId());
        policy.requireCanWork(by, current, "link task lists to tickets");
        if (current.status().isTerminal()) {
            throw new ConflictException("Ticket " + ticketId + " is " + current.status()
                    + " and can no longer change.");
        }
        if (current.assigneeId() == null) {
            throw new ConflictException("Ticket " + ticketId + " has no assignee yet; assign it first.");
        }
        if (current.listId() != null && !current.listId().equals(listId)) {
            throw new ConflictException("Ticket " + ticketId + " already follows task list " + current.listId() + ".")
                    .with("listId", current.listId());
        }
        AgentId assignee = AgentId.of(current.assigneeId());
        if (!belongsTo(listId, assignee)) {
            throw new BadRequestException("Task list " + listId + " does not belong to the assignee " + assignee + ".")
                    .with("listId", listId);
        }
        TicketLinkTarget target = linkTargets.getIfAvailable();
        if (target == null) {
            throw new IllegalStateException("No task-list service is available to link tickets.");
        }
        return target.attachTicket(assignee, listId, ticketId);
    }

    /** v0.0.20 🍊 Tickets of a room (the latest 500) in creation order. */
    public List<Ticket> list(String roomId) {
        if (roomId == null || !rooms.exists(roomId)) {
            throw new NotFoundException("Room " + roomId + " does not exist.");
        }
        return repository.findByRoom(roomId, LIST_LIMIT).stream().map(row -> row.toView(time)).toList();
    }

    /** v0.0.20 🍊 One ticket or NOT_FOUND. */
    public Ticket get(String ticketId) {
        return require(ticketId).toView(time);
    }

    /** v0.0.20 🍊 System transition for the task-list service: links the ticket and follows the list's phase. */
    public Optional<Ticket> syncWithList(String ticketId, String listId, TicketStatus target) {
        if (ticketId == null || repository.findById(ticketId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mutate(ticketId, row -> {
            // Tickets that ended, moved to another assignee or follow another list are never touched by a list.
            if (row.status().isTerminal() || row.assigneeId() == null
                    || !belongsTo(listId, AgentId.of(row.assigneeId()))
                    || (row.listId() != null && !row.listId().equals(listId))) {
                return row;
            }
            Instant now = time.nowInstant();
            TicketRow linked = row.listId() == null ? row.linkedTo(listId, now) : row;
            TicketStatus next = row.status().following(target);
            return next == null ? linked : linked.withStatus(next, now);
        }));
    }

    /** v0.0.20 🍊 Locks, changes and version-checks one ticket in a transaction; an unchanged row is not written. */
    private Ticket mutate(String ticketId, UnaryOperator<TicketRow> change) {
        TicketRow saved = tx.execute(status -> {
            TicketRow current = repository.lockById(ticketId).orElseThrow(() -> notFound(ticketId));
            TicketRow next = change.apply(current);
            if (next == current) {
                return current;
            }
            if (!repository.update(next, current.version())) {
                throw new ConflictException("Ticket " + ticketId + " was changed concurrently; please retry.");
            }
            TicketRow committed = next.withVersion(current.version() + 1);
            publishAfterCommit(committed.toView(time));
            return committed;
        });
        return saved.toView(time);
    }

    /** v0.0.20 🍊 Loads a ticket or throws NOT_FOUND. */
    private TicketRow require(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            throw new BadRequestException("A ticket id is required.");
        }
        return repository.findById(ticketId).orElseThrow(() -> notFound(ticketId));
    }

    /** v0.0.20 🍊 The profile of an agent that can take tickets in the room (present and a member). */
    private AgentProfile requireAssignable(AgentId agentId, String roomId) {
        AgentProfile profile = agents.require(agentId);
        if (!profile.isPresent()) {
            throw new ConflictException(profile.name() + " is retired and cannot take tickets.")
                    .forAgent(agentId.value());
        }
        if (!profile.roomId().equals(roomId)) {
            throw new BadRequestException(profile.name() + " is not a member of room " + roomId + ".")
                    .forAgent(agentId.value());
        }
        return profile;
    }

    /** v0.0.20 🍊 Publishes ticket.upsert once the surrounding transaction commits (immediately when there is none). */
    private void publishAfterCommit(Ticket ticket) {
        Runnable publish = () -> hub.publish(ticket.roomId(), EventType.TICKET_UPSERT, ticket.assigneeId(), ticket);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** v0.0.20 🍊 Runs the publication after a successful commit only. */
            @Override
            public void afterCommit() {
                publish.run();
            }
        });
    }

    /** v0.0.20 🍊 True when the list id is a task-list id owned by the agent ({@code list-<agentHex>-<10hex>}). */
    private static boolean belongsTo(String listId, AgentId agentId) {
        return listId != null && IdGen.isRecordId(listId)
                && listId.startsWith(DataName.TASK_LIST.prefix() + "-" + agentId.hex() + "-");
    }

    /** v0.0.20 🍊 NOT_FOUND for an unknown ticket. */
    private static YuzuException notFound(String ticketId) {
        return new NotFoundException("Ticket " + ticketId + " does not exist.").with("ticketId", ticketId);
    }
}
