package ai.yuzu.monitor;

/** v0.0.12 🍊 Desk state of an agent on the office floor (contract type DeskState); a higher priority wins. */
public enum DeskState {
    IDLE(0),
    WORKING(1),
    THINKING(2),
    TALKING(3),
    WAITING(4),
    PAUSED(5),
    ERROR(6);

    private final int priority;

    /** v0.0.12 🍊 Binds the display priority of the state. */
    DeskState(int priority) {
        this.priority = priority;
    }

    /** v0.0.12 🍊 Display priority: ERROR > PAUSED > WAITING > TALKING > THINKING > WORKING > IDLE. */
    public int priority() {
        return priority;
    }

    /** v0.0.12 🍊 True when a span may declare this state (PAUSED and ERROR are only derived by the board). */
    public boolean declarable() {
        return this != PAUSED && this != ERROR;
    }
}
