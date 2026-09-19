package ai.yuzu.realtime;

import ai.yuzu.common.time.NaturalTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.3 🍊 Verifies live delivery, gap-free replay, resync, room filtering and ordering under concurrency. */
class SseHubTest {

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final SseHub hub = new SseHub(new ObjectMapper(),
            new NaturalTime(Clock.systemUTC(), ZoneId.of("America/Los_Angeles")), timer);

    @AfterEach
    void tearDown() {
        hub.shutdown();
        timer.shutdownNow();
    }

    /** v0.0.3 🍊 A live client gets HELLO then events in publish order. */
    @Test
    void liveClientReceivesEventsInOrder() throws Exception {
        RecordingSink sink = new RecordingSink();
        hub.connect("room-0001", 0, sink);
        hub.publish("room-0001", EventType.CHAT_MESSAGE, null, Map.of("n", 1));
        hub.publish("room-0001", EventType.CHAT_MESSAGE, null, Map.of("n", 2));
        sink.awaitCount(3);
        assertThat(sink.types()).containsExactly(EventType.HELLO, EventType.CHAT_MESSAGE, EventType.CHAT_MESSAGE);
    }

    /** v0.0.3 🍊 A reconnecting client replays exactly the events after its cursor, then continues live. */
    @Test
    void reconnectReplaysWithoutGaps() throws Exception {
        long first = hub.publish("room-0001", EventType.CHAT_MESSAGE, null, Map.of("n", 1)).id();
        long second = hub.publish("room-0001", EventType.CHAT_MESSAGE, null, Map.of("n", 2)).id();
        long third = hub.publish("room-0001", EventType.TASK_LIST, "agent-3fa9", Map.of("n", 3)).id();
        hub.publish("room-0001", EventType.CHAT_DELTA, null, Map.of("transient", true));

        RecordingSink sink = new RecordingSink();
        hub.connect("room-0001", first, sink);
        long fourth = hub.publish("room-0001", EventType.CHAT_MESSAGE, null, Map.of("n", 4)).id();
        sink.awaitCount(4);
        assertThat(sink.ids()).containsExactly(-1L, second, third, fourth);
    }

    /** v0.0.3 🍊 A client whose cursor fell out of the buffer is told to re-bootstrap. */
    @Test
    void staleClientGetsResync() throws Exception {
        hub.publish("room-0001", EventType.CHAT_MESSAGE, null, Map.of("n", 1));
        RecordingSink sink = new RecordingSink();
        hub.connect("room-0001", 1, sink);
        sink.awaitCount(2);
        assertThat(sink.types()).containsExactly(EventType.HELLO, EventType.RESYNC);
    }

    /** v0.0.3 🍊 Events of another room are filtered; platform-wide events reach everyone. */
    @Test
    void roomsAreFiltered() throws Exception {
        RecordingSink sink = new RecordingSink();
        hub.connect("room-aaaa", 0, sink);
        hub.publish("room-bbbb", EventType.CHAT_MESSAGE, null, Map.of("n", 1));
        hub.publishAll(EventType.ERROR, null, Map.of("code", "INTERNAL"));
        sink.awaitCount(2);
        assertThat(sink.types()).containsExactly(EventType.HELLO, EventType.ERROR);
    }

    /** v0.0.3 🍊 Under concurrent publishing, a mid-stream reconnect still sees strictly increasing, gap-free ids. */
    @Test
    void concurrentPublishingKeepsOrder() throws Exception {
        int threads = 8;
        int perThread = 300;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> publishers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            publishers.add(Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < perThread; i++) {
                    hub.publish("room-0001", EventType.MODULE_EVENT, null, Map.of("i", i));
                }
            }));
        }
        long before = hub.currentCursor();
        start.countDown();
        Thread.sleep(5);
        RecordingSink sink = new RecordingSink();
        hub.connect("room-0001", before, sink);
        for (Thread p : publishers) {
            p.join();
        }
        sink.awaitCount(1 + threads * perThread);
        List<Long> ids = sink.ids().subList(1, sink.ids().size());
        assertThat(ids).hasSize(threads * perThread);
        for (int i = 1; i < ids.size(); i++) {
            assertThat(ids.get(i)).isEqualTo(ids.get(i - 1) + 1);
        }
    }

    /** v0.0.3 🍊 In-memory sink recording what the client would have received. */
    private static final class RecordingSink implements EventSink {

        private final List<SerializedEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void send(SerializedEvent event) {
            events.add(event);
        }

        @Override
        public void close() {
        }

        void awaitCount(int count) throws InterruptedException {
            awaitUntil(list -> list.size() >= count);
        }

        void awaitUntil(Predicate<List<SerializedEvent>> condition) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!condition.test(events) && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertThat(condition.test(events)).as("events: %s", types()).isTrue();
        }

        List<EventType> types() {
            return events.stream().map(SerializedEvent::type).toList();
        }

        List<Long> ids() {
            return events.stream().map(SerializedEvent::id).toList();
        }
    }
}
