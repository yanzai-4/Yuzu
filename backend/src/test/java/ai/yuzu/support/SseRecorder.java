package ai.yuzu.support;

import ai.yuzu.realtime.EventSink;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SerializedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.12 🍊 In-memory SSE client that records every event it receives, for assertions on published events. */
public final class SseRecorder implements EventSink {

    private final ObjectMapper mapper;
    private final List<SerializedEvent> events = new CopyOnWriteArrayList<>();

    /** v0.0.12 🍊 Creates a recorder parsing envelopes with the given mapper. */
    public SseRecorder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** v0.0.12 🍊 Records the event. */
    @Override
    public void send(SerializedEvent event) {
        events.add(event);
    }

    /** v0.0.12 🍊 Nothing to release. */
    @Override
    public void close() {
    }

    /** v0.0.12 🍊 Payloads ({@code data}) of the recorded events of a type about one agent, in arrival order. */
    public List<JsonNode> data(EventType type, String agentId) {
        return events.stream().filter(e -> e.type() == type).map(this::parse)
                .filter(envelope -> agentId.equals(envelope.path("agentId").asText()))
                .map(envelope -> envelope.get("data")).toList();
    }

    /** v0.0.12 🍊 Waits up to five seconds for the condition, then asserts it. */
    public void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    /** v0.0.12 🍊 Parses one serialized envelope. */
    private JsonNode parse(SerializedEvent event) {
        try {
            return mapper.readTree(event.json());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unparseable event " + event.json(), e);
        }
    }
}
