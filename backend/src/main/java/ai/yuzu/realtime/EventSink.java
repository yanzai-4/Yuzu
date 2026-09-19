package ai.yuzu.realtime;

import java.io.IOException;

/** v0.0.3 🍊 Transport-agnostic destination of one client's event stream (SSE in production, in-memory in tests). */
public interface EventSink {

    /** v0.0.3 🍊 Writes one event; throws when the client is gone. */
    void send(SerializedEvent event) throws IOException;

    /** v0.0.3 🍊 Closes the stream (the client will reconnect and replay). */
    void close();
}
