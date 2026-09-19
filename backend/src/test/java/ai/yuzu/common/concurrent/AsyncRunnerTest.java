package ai.yuzu.common.concurrent;

import ai.yuzu.common.error.CancelledException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.1 🍊 Verifies that background failures are never lost and cancellation interrupts work. */
class AsyncRunnerTest {

    /** v0.0.1 🍊 A throwing task reaches every sink, even when one sink itself fails. */
    @Test
    void failuresReachAllSinks() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        ErrorSink broken = (c, a, e) -> {
            throw new IllegalStateException("sink broken");
        };
        ErrorSink recording = (c, a, e) -> {
            seen.add(c + "|" + a + "|" + e.getMessage());
            latch.countDown();
        };
        AsyncRunner runner = new AsyncRunner(Executors.newVirtualThreadPerTaskExecutor(), List.of(broken, recording));
        runner.run("tool", "agent-3fa9", () -> {
            throw new IllegalArgumentException("boom");
        });
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(seen).containsExactly("tool|agent-3fa9|boom");
    }

    /** v0.0.1 🍊 supply() propagates the failure to the future. */
    @Test
    void supplyPropagates() {
        AsyncRunner runner = new AsyncRunner(Executors.newVirtualThreadPerTaskExecutor(), List.of((c, a, e) -> { }));
        assertThatThrownBy(() -> runner.supply("x", null, () -> {
            throw new IllegalStateException("nope");
        }).join()).isInstanceOf(CompletionException.class).hasRootCauseMessage("nope");
    }

    /** v0.0.1 🍊 Cancelling a token interrupts a bound sleeping thread. */
    @Test
    void cancelInterruptsBoundThread() throws Exception {
        CancelToken token = new CancelToken();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        Thread worker = Thread.ofVirtual().start(() -> {
            try (CancelToken.Binding ignored = token.bindCurrentThread()) {
                started.countDown();
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        });
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        token.cancel("stop");
        assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        worker.join();
        assertThatThrownBy(token::throwIfCancelled).isInstanceOf(CancelledException.class).hasMessage("stop");
    }
}
