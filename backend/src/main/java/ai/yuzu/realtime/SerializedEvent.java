package ai.yuzu.realtime;

/**
 * v0.0.3 🍊 An event serialized once and shared by every connected client (no per-client re-encoding).
 *
 * @param id     event cursor
 * @param type   event type
 * @param roomId room the event belongs to ("*" for platform-wide events)
 * @param json   the serialized {@link EventEnvelope}
 */
public record SerializedEvent(long id, EventType type, String roomId, String json) {
}
