package ai.yuzu.task.list;

/** v0.0.20 🍊 Task list lifecycle: ACTIVE → AWAITING_APPROVAL (all items finished) → ARCHIVED (publisher approved). */
public enum TaskListStatus {
    ACTIVE,
    AWAITING_APPROVAL,
    ARCHIVED;

    /** v0.0.20 🍊 True while the list is the agent's current list (ACTIVE or AWAITING_APPROVAL). */
    public boolean isOpen() {
        return this != ARCHIVED;
    }
}
