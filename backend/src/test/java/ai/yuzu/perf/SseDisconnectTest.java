package ai.yuzu.perf;

import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.realtime.EventSink;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SerializedEvent;
import ai.yuzu.realtime.SseEmitterSink;
import ai.yuzu.realtime.SseHub;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * v0.0.31 🍊 A browser that vanishes mid-stream must cost nothing: no blocked publisher, no leaked client.
 *
 * <p>Covers a sink that starts throwing halfway through, a consumer too slow to keep up (bounded queue),
 * the replay of the gap after the client comes back, and the emitter sink's own close path.</p>
 */
class SseDisconnectTest {

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final SseHub hub = new SseHub(new ObjectMapper(),
            new NaturalTime(Clock.systemUTC(), ZoneId.of("America/Los_Angeles")), timer);

    @AfterEach
    void tearDown() {
        hub.shutdown();
        timer.shutdownNow();
    }

    /** v0.0.31 🍊 A sink that fails mid-stream unregisters its client; the other clients keep receiving. */
    @Test
    void disconnectMidStreamDropsOnlyThatClient() throws Exception {
        FailingSink dying = new FailingSink(2);
        RecordingSink healthy = new RecordingSink();
        hub.connect("room-0001", 0, dying);
        hub.connect("room-0001", 0, healthy);
        assertThat(hub.clientCount()).isEqualTo(2);

        for (int i = 0; i < 6; i++) {
            hub.publish("room-0001", EventType.CHAT_MESSAGE, null, Map.of("n", i));
        }
        await(() -> hub.clientCount() == 1);
        healthy.awaitCount(7);
        assertThat(healthy.types()).hasSize(7).startsWith(EventType.HELLO);
        assertThat(dying.closed()).isTrue();
        assertThat(hub.bufferedEvents()).hasSize(6);
    }

    /** v0.0.31 🍊 A consumer that never drains is dropped instead of blocking the publisher. */
    @Test
    void slowConsumerIsDroppedWithoutBlockingThePublisher() throws Exception {
        BlockingSink stuck = new BlockingSink();
        hub.connect("room-0001", 0, stuck);
        RecordingSink healthy = new RecordingSink();
        hub.connect("room-0001", 0, healthy);

        long startedNanos = System.nanoTime();
        for (int i = 0; i < 4_000; i++) {
            hub.publish("room-0001", EventType.MODULE_EVENT, null, Map.of("i", i));
        }
        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000;

        await(() -> hub.clientCount() == 1);
        assertThat(elapsedMillis).as("publishing must never wait for a stuck client").isLessThan(20_000);
        healthy.awaitCount(4_001);
        stuck.release();
    }

    /** v0.0.31 🍊 After a disconnect the client reconnects with its cursor and replays exactly the gap. */
    @Test
    void reconnectReplaysTheGapAfterADisconnect() throws Exception {
        FailingSink dying = new FailingSink(1);
        hub.connect("room-0001", 0, dying);
        long first = hub.publish("room-0001", EventType.CHAT_MESSAGE, null, Map.of("n", 1)).id();
        await(() -> hub.clientCount() == 0);
        long second = hub.publish("room-0001", EventType.CHAT_MESSAGE, null, Map.of("n", 2)).id();
        long third = hub.publish("room-0001", EventType.CHAT_MESSAGE, null, Map.of("n", 3)).id();

        RecordingSink reconnected = new RecordingSink();
        hub.connect("room-0001", first, reconnected);
        reconnected.awaitCount(3);
        assertThat(reconnected.ids()).containsExactly(-1L, second, third);
    }

    /** v0.0.31 🍊 Heartbeats to a gone client are harmless and do not resurrect it. */
    @Test
    void heartbeatsToAGoneClientAreHarmless() throws Exception {
        FailingSink dying = new FailingSink(0);
        hub.connect("room-0001", 0, dying);
        await(() -> hub.clientCount() == 0);
        assertThatCode(() -> hub.publishAll(EventType.USAGE_TICK, null, Map.of("tick", 1))).doesNotThrowAnyException();
        assertThat(hub.clientCount()).isZero();
    }

    /** v0.0.31 🍊 Closing an emitter whose HTTP response is already gone never throws. */
    @Test
    void emitterSinkCloseIsSafeTwice() {
        SseEmitter emitter = new SseEmitter(0L);
        SseEmitterSink sink = new SseEmitterSink(emitter);
        assertThatCode(sink::close).doesNotThrowAnyException();
        assertThatCode(sink::close).doesNotThrowAnyException();
    }

    /** v0.0.31 🍊 Polls a condition with a hard deadline. */
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not reached in time");
            }
            Thread.sleep(5);
        }
    }

    /** v0.0.31 🍊 Sink that behaves for a while and then fails like a closed HTTP response. */
    private static final class FailingSink implements EventSink {

        private final AtomicInteger sent = new AtomicInteger();
        private final int failAfter;
        private volatile boolean closed;

        FailingSink(int failAfter) {
            this.failAfter = failAfter;
        }

        @Override
        public void send(SerializedEvent event) throws IOException {
            if (sent.incrementAndGet() > failAfter) {
                throw new IOException("Broken pipe");
            }
        }

        @Override
        public void close() {
            closed = true;
        }

        boolean closed() {
            return closed;
        }
    }

    /** v0.0.31 🍊 Sink that never returns from its first write (a browser that stopped reading). */
    private static final class BlockingSink implements EventSink {

        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void send(SerializedEvent event) {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
            release.countDown();
        }

        void release() {
            release.countDown();
        }
    }

    /** v0.0.31 🍊 Sink recording everything the client received. */
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
            await(() -> events.size() >= count);
        }

        List<EventType> types() {
            return events.stream().map(SerializedEvent::type).toList();
        }

        List<Long> ids() {
            return events.stream().map(SerializedEvent::id).toList();
        }
    }
}
