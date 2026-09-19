package ai.yuzu.monitor;

/** v0.0.12 🍊 Phase of a module event (contract type EventPhase). */
public enum EventPhase {
    START,
    STATE,
    END,
    ERROR,
    CANCELLED,
    INFO;

    /** v0.0.12 🍊 True for the phases that close a span (END, ERROR, CANCELLED). */
    public boolean terminal() {
        return this == END || this == ERROR || this == CANCELLED;
    }

    /** v0.0.12 🍊 Text used when a module reports this phase with a blank text. */
    public String defaultText() {
        return switch (this) {
            case START -> "Started";
            case STATE -> "Working";
            case END -> "done";
            case ERROR -> "Failed";
            case CANCELLED -> "Cancelled";
            case INFO -> "Noted";
        };
    }
}
