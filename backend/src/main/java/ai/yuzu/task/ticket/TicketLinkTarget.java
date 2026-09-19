package ai.yuzu.task.ticket;

import ai.yuzu.common.id.AgentId;

/** v0.0.10 🍊 Task-list side of a ticket link, implemented by the task-list service (keeps both sides consistent). */
public interface TicketLinkTarget {

    /** v0.0.10 🍊 Records the ticket on the assignee's open list, moves the ticket along, and returns the ticket. */
    Ticket attachTicket(AgentId assignee, String listId, String ticketId);
}
