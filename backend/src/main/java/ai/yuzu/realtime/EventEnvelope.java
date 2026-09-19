package ai.yuzu.realtime;

/**
 * v0.0.3 🍊 JSON body of every realtime event.
 *
 * @param id      monotonic event cursor (also the SSE {@code id:}); used for Last-Event-ID replay
 * @param type    event type (also the SSE {@code event:} name)
 * @param roomId  room the event belongs to
 * @param agentId agent the event is about, or null
 * @param time    natural-language time the event was published
 * @param data    event payload (a DTO defined by the publishing feature)
 */
public record EventEnvelope(long id, EventType type, String roomId, String agentId, String time, Object data) {
}
