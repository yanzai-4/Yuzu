package ai.yuzu.task.list;

import ai.yuzu.common.id.AgentId;

import java.time.Instant;

/** v0.0.20 🍊 One row of the agent-scoped task_item table (domain form); new items have a null id until inserted. */
public record TaskItemRow(AgentId agentId, String id, String listId, int ord, String text, TaskItemState state,
                          String note, String struckReason, Instant createdAt, Instant updatedAt) {

    /** v0.0.20 🍊 A new TODO item that is not persisted yet. */
    public static TaskItemRow fresh(AgentId agentId, int ord, String text, Instant now) {
        return new TaskItemRow(agentId, null, null, ord, text, TaskItemState.TODO, null, null, now, now);
    }

    /** v0.0.20 🍊 True until the item has been inserted. */
    public boolean isNew() {
        return id == null;
    }

    /** v0.0.20 🍊 Copy in another state. */
    public TaskItemRow withState(TaskItemState next, Instant now) {
        return new TaskItemRow(agentId, id, listId, ord, text, next, note, struckReason, createdAt, now);
    }

    /** v0.0.20 🍊 Copy with a new note text. */
    public TaskItemRow withNote(String newNote, Instant now) {
        return new TaskItemRow(agentId, id, listId, ord, text, state, newNote, struckReason, createdAt, now);
    }

    /** v0.0.20 🍊 Copy struck through with a reason (the item stays visible). */
    public TaskItemRow struck(String reason, Instant now) {
        return new TaskItemRow(agentId, id, listId, ord, text, TaskItemState.STRUCK, note, reason, createdAt, now);
    }

    /** v0.0.20 🍊 Copy placed in a list (set right before a new item is inserted). */
    public TaskItemRow inList(String newListId) {
        return new TaskItemRow(agentId, id, newListId, ord, text, state, note, struckReason, createdAt, updatedAt);
    }

    /** v0.0.20 🍊 API view (contract type {@code TaskItem}). */
    public TaskItem toView() {
        return new TaskItem(id, ord, text, state, note, struckReason);
    }
}
