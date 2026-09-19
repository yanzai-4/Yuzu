package ai.yuzu.task.ticket;

import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.task.Actor;

import java.time.Instant;

/** v0.0.20 🍊 One row of the room-scoped ticket table (domain form: UTC instants, assigner, optimistic version). */
public record TicketRow(String roomId, String id, String title, String detail, TicketStatus status, Actor creator,
                        String assigneeId, Actor assignedBy, String requesterId, String requesterName,
                        String sourceMessageId, String listId, int version, Instant createdAt, Instant updatedAt) {

    /** v0.0.20 🍊 Copy carrying the id generated on insert. */
    public TicketRow withId(String newId) {
        return new TicketRow(roomId, newId, title, detail, status, creator, assigneeId, assignedBy, requesterId,
                requesterName, sourceMessageId, listId, version, createdAt, updatedAt);
    }

    /** v0.0.20 🍊 Copy with a new status. */
    public TicketRow withStatus(TicketStatus next, Instant now) {
        return new TicketRow(roomId, id, title, detail, next, creator, assigneeId, assignedBy, requesterId,
                requesterName, sourceMessageId, listId, version, createdAt, now);
    }

    /** v0.0.20 🍊 Copy assigned to an agent by an actor (status ASSIGNED). */
    public TicketRow assignedTo(String agentId, Actor by, Instant now) {
        return new TicketRow(roomId, id, title, detail, TicketStatus.ASSIGNED, creator, agentId, by, requesterId,
                requesterName, sourceMessageId, listId, version, createdAt, now);
    }

    /** v0.0.20 🍊 Copy linked to the assignee's task list. */
    public TicketRow linkedTo(String newListId, Instant now) {
        return new TicketRow(roomId, id, title, detail, status, creator, assigneeId, assignedBy, requesterId,
                requesterName, sourceMessageId, newListId, version, createdAt, now);
    }

    /** v0.0.20 🍊 Copy with the version the database now holds. */
    public TicketRow withVersion(int newVersion) {
        return new TicketRow(roomId, id, title, detail, status, creator, assigneeId, assignedBy, requesterId,
                requesterName, sourceMessageId, listId, newVersion, createdAt, updatedAt);
    }

    /** v0.0.20 🍊 API view with natural-language times (contract type {@code Ticket}). */
    public Ticket toView(NaturalTime time) {
        return new Ticket(id, roomId, title, detail, status, creator.name(), assigneeId, requesterName, listId,
                time.compact(createdAt), time.compact(updatedAt), creator, assignedBy);
    }
}
