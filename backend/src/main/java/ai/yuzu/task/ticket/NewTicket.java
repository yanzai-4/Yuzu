package ai.yuzu.task.ticket;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.task.Actor;

/** v0.0.20 🍊 What a new ticket says (optional assignee, requester and source chat message); start with {@link #of}. */
public record NewTicket(String title, String detail, AgentId assignee, Actor requester, String sourceMessageId) {

    /** v0.0.20 🍊 A ticket with a title and detail, unassigned. */
    public static NewTicket of(String title, String detail) {
        return new NewTicket(title, detail, null, null, null);
    }

    /** v0.0.20 🍊 Copy assigned to an agent at creation. */
    public NewTicket assignedTo(AgentId agentId) {
        return new NewTicket(title, detail, agentId, requester, sourceMessageId);
    }

    /** v0.0.20 🍊 Copy recording who asked for the work. */
    public NewTicket requestedBy(Actor actor) {
        return new NewTicket(title, detail, assignee, actor, sourceMessageId);
    }

    /** v0.0.20 🍊 Copy recording the chat message the request came from. */
    public NewTicket fromMessage(String messageId) {
        return new NewTicket(title, detail, assignee, requester, messageId);
    }
}
