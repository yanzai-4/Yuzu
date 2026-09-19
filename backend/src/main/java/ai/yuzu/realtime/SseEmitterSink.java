package ai.yuzu.realtime;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/** v0.0.3 🍊 {@link EventSink} backed by a Spring {@link SseEmitter}. */
public final class SseEmitterSink implements EventSink {

    private final SseEmitter emitter;

    /** v0.0.3 🍊 Wraps an emitter created by the stream controller. */
    public SseEmitterSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    /** v0.0.3 🍊 Writes "id / event / data" lines; control events (id < 0) carry no id so replay is unaffected. */
    @Override
    public void send(SerializedEvent event) throws IOException {
        SseEmitter.SseEventBuilder builder = SseEmitter.event().name(event.type().wireName());
        if (event.id() >= 0) {
            builder.id(Long.toString(event.id()));
        }
        emitter.send(builder.data(event.json(), MediaType.APPLICATION_JSON));
    }

    /** v0.0.3 🍊 Completes the HTTP response. */
    @Override
    public void close() {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // The client is already gone.
        }
    }
}
