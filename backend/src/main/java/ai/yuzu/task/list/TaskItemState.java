package ai.yuzu.task.list;

/** v0.0.20 🍊 State of a task item; items are never deleted, only checked (DONE) or struck through (STRUCK). */
public enum TaskItemState {
    TODO,
    DOING,
    DONE,
    STRUCK;

    /** v0.0.20 🍊 True for DONE and STRUCK (nothing left to do on the item). */
    public boolean isFinished() {
        return this == DONE || this == STRUCK;
    }
}
