package ai.yuzu.task.ticket;

import ai.yuzu.agent.Permission;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.task.Actor;
import ai.yuzu.task.ActorResolver;

/** v0.0.20 🍊 Code-level rules for who may create, assign, work on, approve or cancel a ticket. */
final class TicketPolicy {

    private final ActorResolver actors;

    /** v0.0.20 🍊 Creates the policy over the actor resolver. */
    TicketPolicy(ActorResolver actors) {
        this.actors = actors;
    }

    /** v0.0.20 🍊 Managing tickets (create, assign, cancel) needs a human or an agent holding TASK_ASSIGN. */
    void requireCanManage(Actor actor, String action) {
        actors.require(actor, Permission.TASK_ASSIGN, action);
    }

    /** v0.0.20 🍊 Working on a ticket needs its assignee, a human, or a TASK_ASSIGN agent. */
    void requireCanWork(Actor actor, TicketRow ticket, String action) {
        if (actor.isAgent() && actor.id().equals(ticket.assigneeId())) {
            return;
        }
        requireCanManage(actor, action);
    }

    /** v0.0.20 🍊 Checks the actor may move the ticket to the target status. */
    void requireCanMove(Actor actor, TicketRow ticket, TicketStatus target) {
        switch (target) {
            case IN_PROGRESS, DONE -> requireCanWork(actor, ticket, "move tickets to " + target);
            case APPROVED -> requireCanApprove(actor, ticket);
            default -> requireCanManage(actor, "move tickets to " + target);
        }
    }

    /** v0.0.20 🍊 Approval needs a human, or whoever assigned the ticket holding TASK_APPROVE (never the assignee). */
    void requireCanApprove(Actor actor, TicketRow ticket) {
        if (actor.isHuman()) {
            return;
        }
        if (actor.id().equals(ticket.assigneeId())) {
            throw new PermissionDeniedException("An assignee cannot approve its own ticket; whoever assigned it or a "
                    + "human must.").forAgent(actor.id());
        }
        Actor publisher = ticket.assignedBy() != null ? ticket.assignedBy() : ticket.creator();
        if (!publisher.sameAs(actor)) {
            throw new PermissionDeniedException("Only " + publisher.describe() + ", who assigned this ticket, or a "
                    + "human can approve it.").forAgent(actor.id());
        }
        actors.require(actor, Permission.TASK_APPROVE, "approve tickets");
    }
}
