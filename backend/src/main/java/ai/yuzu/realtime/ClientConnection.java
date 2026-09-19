package ai.yuzu.realtime;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * v0.0.3 🍊 One connected browser: a bounded outbound queue drained by its own virtual writer thread.
 *
 * <p>Publishers never block on a slow client: when the queue is full the connection is closed and the
 * client reconnects with {@code Last-Event-ID}, replaying what it missed from the ring buffer.</p>
 */
final class ClientConnection {

    private static final int QUEUE_CAPACITY = 2_048;

    private final String id;
    private final String roomId;
    private final EventSink sink;
    private final Runnable onClose;
    private final BlockingQueue<SerializedEvent> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private volatile boolean open = true;
    private volatile Thread writer;

    /** v0.0.3 🍊 Creates a connection; {@link #start()} launches the writer thread. */
    ClientConnection(String id, String roomId, EventSink sink, Runnable onClose) {
        this.id = id;
        this.roomId = roomId;
        this.sink = sink;
        this.onClose = onClose;
    }

    /** v0.0.3 🍊 Starts the virtual writer thread. */
    void start() {
        writer = Thread.ofVirtual().name("sse-" + id).start(this::drainLoop);
    }

    /** v0.0.3 🍊 Non-blocking enqueue; a full queue closes the connection (slow consumer). */
    boolean enqueue(SerializedEvent event) {
        if (!open) {
            return false;
        }
        if (!queue.offer(event)) {
            close();
            return false;
        }
        return true;
    }

    /** v0.0.3 🍊 True when the event belongs to this client's room (or is platform-wide). */
    boolean accepts(SerializedEvent event) {
        return "*".equals(event.roomId()) || roomId.equals(event.roomId());
    }

    /** v0.0.3 🍊 Closes the stream once, stops the writer and unregisters from the hub. */
    void close() {
        if (!open) {
            return;
        }
        open = false;
        Thread current = writer;
        if (current != null && current != Thread.currentThread()) {
            current.interrupt();
        }
        sink.close();
        onClose.run();
    }

    /** v0.0.3 🍊 Connection id. */
    String id() {
        return id;
    }

    /** v0.0.3 🍊 True until the connection is closed. */
    boolean isOpen() {
        return open;
    }

    /** v0.0.3 🍊 Writes queued events in order until the connection closes. */
    private void drainLoop() {
        try {
            while (open) {
                SerializedEvent event = queue.take();
                sink.send(event);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            // Client disconnected; it will reconnect and replay.
        } finally {
            close();
        }
    }
}
