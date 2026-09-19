package ai.yuzu.task.list;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.task.Actor;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** v0.0.10 🍊 One row of the agent-scoped task_list table (domain form: publisher, goal history, instants, version). */
public record TaskListRow(AgentId agentId, String id, String goal, List<GoalChange> goalHistory, TaskListStatus status,
                          Actor publisher, String ticketId, String outcome, String approvedBy, int version,
                          Instant createdAt, Instant updatedAt, Instant completedAt, Instant approvedAt,
                          Instant archivedAt) {

    /** v0.0.10 🍊 Defensive, immutable copy of the goal history. */
    public TaskListRow {
        goalHistory = goalHistory == null ? List.of() : List.copyOf(goalHistory);
    }

    /** v0.0.10 🍊 A new ACTIVE list that is not persisted yet (id assigned on insert). */
    public static TaskListRow fresh(AgentId agentId, String goal, Actor publisher, String ticketId, Instant now) {
        return new TaskListRow(agentId, null, goal, List.of(), TaskListStatus.ACTIVE, publisher, ticketId, null, null,
                0, now, now, null, null, null);
    }

    /** v0.0.10 🍊 Copy carrying the id generated on insert. */
    public TaskListRow withId(String newId) {
        return new TaskListRow(agentId, newId, goal, goalHistory, status, publisher, ticketId, outcome, approvedBy,
                version, createdAt, updatedAt, completedAt, approvedAt, archivedAt);
    }

    /** v0.0.10 🍊 Copy after a batch of operations (goal, history, status and completion time may change). */
    public TaskListRow edited(String newGoal, List<GoalChange> history, TaskListStatus newStatus,
                              Instant newCompletedAt, Instant now) {
        return new TaskListRow(agentId, id, newGoal, history, newStatus, publisher, ticketId, outcome, approvedBy,
                version, createdAt, now, newCompletedAt, approvedAt, archivedAt);
    }

    /** v0.0.10 🍊 Copy waiting for the publisher's approval. */
    public TaskListRow awaitingApproval(Instant now) {
        return new TaskListRow(agentId, id, goal, goalHistory, TaskListStatus.AWAITING_APPROVAL, publisher, ticketId,
                outcome, approvedBy, version, createdAt, now, now, approvedAt, archivedAt);
    }

    /** v0.0.10 🍊 Copy approved and archived with its outcome. */
    public TaskListRow archived(Actor approver, String outcomeText, Instant now) {
        return new TaskListRow(agentId, id, goal, goalHistory, TaskListStatus.ARCHIVED, publisher, ticketId,
                outcomeText, approver.id(), version, createdAt, now, completedAt, now, now);
    }

    /** v0.0.10 🍊 Copy working on a ticket. */
    public TaskListRow withTicket(String newTicketId, Instant now) {
        return new TaskListRow(agentId, id, goal, goalHistory, status, publisher, newTicketId, outcome, approvedBy,
                version, createdAt, now, completedAt, approvedAt, archivedAt);
    }

    /** v0.0.10 🍊 API view with natural-language times and items in display order (contract type {@code TaskList}). */
    public TaskList toView(List<TaskItemRow> items, NaturalTime time) {
        return new TaskList(id, agentId.value(), goal, status, publisher.id(), publisher.name(), ticketId, outcome,
                items.stream().sorted(Comparator.comparingInt(TaskItemRow::ord)).map(TaskItemRow::toView).toList(),
                time.compact(createdAt), archivedAt == null ? null : time.compact(archivedAt), publisher.kind(),
                goalHistory.stream().map(change -> change.toView(time)).toList());
    }
}
