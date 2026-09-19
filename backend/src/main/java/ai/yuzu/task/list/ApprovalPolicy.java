package ai.yuzu.task.list;

import ai.yuzu.agent.Permission;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.task.Actor;
import ai.yuzu.task.ActorResolver;

/** v0.0.10 🍊 Who may approve (and so archive) a finished task list: its publisher or any human, never the owner. */
final class ApprovalPolicy {

    private final ActorResolver actors;

    /** v0.0.10 🍊 Creates the policy over the actor resolver. */
    ApprovalPolicy(ActorResolver actors) {
        this.actors = actors;
    }

    /** v0.0.10 🍊 Humans always pass; an agent must be the list's publisher, not its owner, and hold TASK_APPROVE. */
    void requireCanApprove(Actor approver, TaskListRow list) {
        if (approver.isHuman()) {
            return;
        }
        if (approver.id().equals(list.agentId().value())) {
            throw new PermissionDeniedException("An agent cannot approve its own task list; its publisher ("
                    + list.publisher().describe() + ") or a human must.")
                    .with("listId", list.id()).forAgent(approver.id());
        }
        if (!list.publisher().sameAs(approver)) {
            throw new PermissionDeniedException("Only the publisher (" + list.publisher().describe()
                    + ") or a human can approve task list " + list.id() + ".")
                    .with("listId", list.id()).forAgent(approver.id());
        }
        actors.require(approver, Permission.TASK_APPROVE, "approve task lists");
    }
}
