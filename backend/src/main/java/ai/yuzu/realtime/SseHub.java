package ai.yuzu.realtime;

import ai.yuzu.common.time.NaturalTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * v0.0.3 🍊 Realtime event hub: assigns cursors, keeps a replay ring buffer and fans events out to clients.
 *
 * <p>Publishing and connecting share one short lock, so a reconnecting client receives the replayed
 * events and then live events with no gap and no duplicate. Serialization happens once per event.
 * Cursors start from the boot time in milliseconds × 1000, so they keep increasing across restarts and
 * a stale client is told to {@code resync} (re-bootstrap).</p>
 */
@Component
public class SseHub {

    private static final int RING_CAPACITY = 5_000;
    private static final long HEARTBEAT_SECONDS = 15;
    private static final long NO_ID = -1;

    private final ObjectMapper mapper;
    private final NaturalTime time;
    private final AtomicLong cursor = new AtomicLong(System.currentTimeMillis() * 1_000);
    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayDeque<SerializedEvent> ring = new ArrayDeque<>(RING_CAPACITY);
    private final Map<String, ClientConnection> clients = new ConcurrentHashMap<>();
    private final ScheduledFuture<?> heartbeat;

    /** v0.0.3 🍊 Creates the hub and schedules heartbeats on the timer thread. */
    public SseHub(ObjectMapper mapper, NaturalTime time, ScheduledExecutorService timerExecutor) {
        this.mapper = mapper;
        this.time = time;
        this.heartbeat = timerExecutor.scheduleAtFixedRate(this::sendHeartbeats, HEARTBEAT_SECONDS,
                HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /** v0.0.3 🍊 Publishes an event to every client of a room. */
    public SerializedEvent publish(String roomId, EventType type, String agentId, Object data) {
        lock.lock();
        try {
            long id = cursor.incrementAndGet();
            SerializedEvent event = new SerializedEvent(id, type, roomId,
                    serialize(new EventEnvelope(id, type, roomId, agentId, time.compact(time.nowInstant()), data)));
            if (type.replayable()) {
                if (ring.size() == RING_CAPACITY) {
                    ring.removeFirst();
                }
                ring.addLast(event);
            }
            for (ClientConnection client : clients.values()) {
                if (client.accepts(event)) {
                    client.enqueue(event);
                }
            }
            return event;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.3 🍊 Publishes a platform-wide event (every room). */
    public SerializedEvent publishAll(EventType type, String agentId, Object data) {
        return publish("*", type, agentId, data);
    }

    /**
     * v0.0.3 🍊 Registers a client, replaying everything after {@code after} (0 = live only).
     *
     * @return the connection id
     */
    public String connect(String roomId, long after, EventSink sink) {
        String connectionId = UUID.randomUUID().toString().substring(0, 8);
        ClientConnection client = new ClientConnection(connectionId, roomId, sink, () -> clients.remove(connectionId));
        lock.lock();
        try {
            long current = cursor.get();
            client.enqueue(control(EventType.HELLO, roomId, Map.of("cursor", current, "connectionId", connectionId)));
            if (after > 0 && after < current) {
                SerializedEvent oldest = ring.peekFirst();
                if (oldest == null || oldest.id() > after + 1) {
                    client.enqueue(control(EventType.RESYNC, roomId, Map.of("cursor", current)));
                } else {
                    for (SerializedEvent event : ring) {
                        if (event.id() > after && client.accepts(event)) {
                            client.enqueue(event);
                        }
                    }
                }
            }
            clients.put(connectionId, client);
        } finally {
            lock.unlock();
        }
        client.start();
        return connectionId;
    }

    /** v0.0.3 🍊 Number of connected clients. */
    public int clientCount() {
        return clients.size();
    }

    /** v0.0.3 🍊 The last assigned cursor. */
    public long currentCursor() {
        return cursor.get();
    }

    /** v0.0.3 🍊 Snapshot of the replay buffer (tests and diagnostics). */
    public List<SerializedEvent> bufferedEvents() {
        lock.lock();
        try {
            return List.copyOf(ring);
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.3 🍊 Stops heartbeats and closes every client on shutdown. */
    @PreDestroy
    public void shutdown() {
        heartbeat.cancel(false);
        clients.values().forEach(ClientConnection::close);
    }

    /** v0.0.3 🍊 Enqueues a heartbeat for every client (keeps proxies from closing idle streams). */
    private void sendHeartbeats() {
        SerializedEvent beat = control(EventType.HEARTBEAT, "*", Map.of("cursor", cursor.get()));
        clients.values().forEach(client -> client.enqueue(beat));
    }

    /** v0.0.3 🍊 Builds a control event that carries no replay id. */
    private SerializedEvent control(EventType type, String roomId, Object data) {
        return new SerializedEvent(NO_ID, type, roomId,
                serialize(new EventEnvelope(NO_ID, type, roomId, null, time.compact(time.nowInstant()), data)));
    }

    /** v0.0.3 🍊 Serializes an envelope; serialization failures are programming errors. */
    private String serialize(EventEnvelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + envelope.type(), e);
        }
    }
}
