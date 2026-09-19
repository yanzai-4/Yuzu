package ai.yuzu.monitor;

import ai.yuzu.common.concurrent.AsyncRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.12 🍊 Throttle semantics: immediate first run, one trailing run per burst, no overlap, failures contained. */
class CoalescingTriggerTest {

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final List<Throwable> reported = new CopyOnWriteArrayList<>();
    private final AsyncRunner runner = new AsyncRunner(Executors.newVirtualThreadPerTaskExecutor(),
            List.of((context, agentId, error) -> reported.add(error)));

    /** v0.0.12 🍊 Stops the timer. */
    @AfterEach
    void tearDown() {
        timer.shutdownNow();
    }

    /** v0.0.12 🍊 The first fire runs at once; a burst inside the interval becomes exactly one trailing run. */
    @Test
    void firstRunIsImmediateAndBurstsCoalesce() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        CoalescingTrigger trigger = new CoalescingTrigger("test", "agent-1a2b", Duration.ofMillis(600), timer, runner,
                runs::incrementAndGet);
        trigger.fire();
        awaitUntil(() -> runs.get() == 1, 200);
        for (int i = 0; i < 100; i++) {
            trigger.fire();
        }
        Thread.sleep(100);
        assertThat(runs.get()).isEqualTo(1);
        awaitUntil(() -> runs.get() == 2, 1_500);
        Thread.sleep(800);
        assertThat(runs.get()).isEqualTo(2);
    }

    /** v0.0.12 🍊 Under continuous firing runs never overlap and start at least one interval apart. */
    @Test
    void runsNeverOverlapAndKeepTheInterval() throws Exception {
        List<Long> starts = new CopyOnWriteArrayList<>();
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        CoalescingTrigger trigger = new CoalescingTrigger("test", null, Duration.ofMillis(100), timer, runner, () -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            starts.add(System.nanoTime());
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            running.decrementAndGet();
        });
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < end) {
            trigger.fire();
            Thread.sleep(3);
        }
        Thread.sleep(300);
        assertThat(maxRunning.get()).isEqualTo(1);
        assertThat(starts.size()).isBetween(5, 12);
        for (int i = 1; i < starts.size(); i++) {
            assertThat((starts.get(i) - starts.get(i - 1)) / 1_000_000).isGreaterThanOrEqualTo(95);
        }
        assertThat(reported).isEmpty();
    }

    /** v0.0.12 🍊 A throwing action is contained and does not stop later runs; a closed trigger never runs again. */
    @Test
    void failuresAreContainedAndClosedTriggersStop() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        CoalescingTrigger trigger = new CoalescingTrigger("test", null, Duration.ofMillis(50), timer, runner, () -> {
            runs.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        trigger.fire();
        awaitUntil(() -> runs.get() == 1, 500);
        Thread.sleep(80);
        trigger.fire();
        awaitUntil(() -> runs.get() == 2, 500);
        trigger.close();
        trigger.fire();
        Thread.sleep(200);
        assertThat(runs.get()).isEqualTo(2);
        assertThat(reported).isEmpty();
    }

    /** v0.0.12 🍊 Polls a condition until the deadline, then asserts it. */
    private static void awaitUntil(BooleanSupplier condition, long millis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
