package ai.yuzu.task.ticket;

/** v0.0.20 🍊 Ticket lifecycle: OPEN → ASSIGNED → IN_PROGRESS → DONE → APPROVED, or CANCELLED. */
public enum TicketStatus {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    DONE,
    APPROVED,
    CANCELLED;

    /** v0.0.20 🍊 True for statuses that end the ticket (APPROVED, CANCELLED). */
    public boolean isTerminal() {
        return this == APPROVED || this == CANCELLED;
    }

    /** v0.0.20 🍊 Manual transitions allowed through updateStatus (ASSIGNED is only reached through assign). */
    public boolean canMoveTo(TicketStatus next) {
        return switch (this) {
            case OPEN -> next == CANCELLED;
            case ASSIGNED -> next == IN_PROGRESS || next == DONE || next == CANCELLED;
            case IN_PROGRESS -> next == DONE || next == CANCELLED;
            case DONE -> next == APPROVED || next == IN_PROGRESS || next == CANCELLED;
            case APPROVED, CANCELLED -> false;
        };
    }

    /** v0.0.20 🍊 Where a linked ticket moves when its task list reaches {@code target}; null keeps the status. */
    TicketStatus following(TicketStatus target) {
        return switch (target) {
            case IN_PROGRESS -> this == ASSIGNED || this == DONE ? IN_PROGRESS : null;
            case DONE -> this == ASSIGNED || this == IN_PROGRESS ? DONE : null;
            case APPROVED -> this == ASSIGNED || this == IN_PROGRESS || this == DONE ? APPROVED : null;
            default -> null;
        };
    }
}
