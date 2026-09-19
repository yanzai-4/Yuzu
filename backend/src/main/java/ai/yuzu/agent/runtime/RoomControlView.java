package ai.yuzu.agent.runtime;

import ai.yuzu.monitor.AgentStatusView;

import java.util.List;

/**
 * v0.0.30 🍊 Result of a room-wide control action (contract type {@code RoomControlResult}).
 *
 * @param roomId   the workgroup the action was applied to
 * @param action   STOP_ALL or RESUME_ALL
 * @param affected how many coworkers actually changed state (0 when everyone was already there)
 * @param statuses the live desk status of every present coworker, so a client can sync in one round trip
 * @param time     when the action ran (natural language)
 */
public record RoomControlView(String roomId, String action, int affected, List<AgentStatusView> statuses,
                              String time) {

    /** v0.0.30 🍊 Action name of "stop every coworker in this room". */
    public static final String STOP_ALL = "STOP_ALL";

    /** v0.0.30 🍊 Action name of "let every paused coworker resume". */
    public static final String RESUME_ALL = "RESUME_ALL";

    /** v0.0.30 🍊 Freezes the status list. */
    public RoomControlView {
        statuses = List.copyOf(statuses);
    }
}
