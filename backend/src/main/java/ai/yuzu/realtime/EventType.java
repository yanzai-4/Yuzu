package ai.yuzu.realtime;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * v0.0.3 🍊 Every realtime event type pushed to the frontend (the SSE {@code event:} name).
 *
 * <p>{@code replayable} events are kept in the ring buffer so a reconnecting client can catch up via
 * {@code Last-Event-ID}; transient ones (deltas, typing, heartbeats, live status ticks) are not.</p>
 */
public enum EventType {
    HELLO("hello", false),
    HEARTBEAT("heartbeat", false),
    RESYNC("resync", false),
    CHAT_MESSAGE("chat.message", true),
    CHAT_DELTA("chat.delta", false),
    CHAT_CARD("chat.card", true),
    CHAT_TYPING("chat.typing", false),
    USER_JOINED("user.joined", true),
    AGENT_UPSERT("agent.upsert", true),
    AGENT_REMOVED("agent.removed", true),
    AGENT_STATUS("agent.status", false),
    MODULE_EVENT("module.event", true),
    TASK_LIST("task.list", true),
    TICKET_UPSERT("ticket.upsert", true),
    USAGE_TICK("usage.tick", false),
    SIM_EMAIL("sim.email", true),
    SIM_TRADE("sim.trade", true),
    SIM_PORTFOLIO("sim.portfolio", true),
    SECURITY_INCIDENT("security.incident", true),
    SETTINGS_CHANGED("settings.changed", true),
    ERROR("error", true);

    private final String wireName;
    private final boolean replayable;

    EventType(String wireName, boolean replayable) {
        this.wireName = wireName;
        this.replayable = replayable;
    }

    /** v0.0.3 🍊 The SSE event name, for example "chat.message". */
    @JsonValue
    public String wireName() {
        return wireName;
    }

    /** v0.0.3 🍊 True when the event is stored for Last-Event-ID replay. */
    public boolean replayable() {
        return replayable;
    }
}
