package ai.yuzu.llm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.11 🍊 The gate never lets more calls through than the class allows. */
class PriorityGateTest {

    /** v0.0.11 🍊 BACKGROUND allows at most 4 concurrent calls; MONITOR at most 2. */
    @Test
    void limitsConcurrency() throws Exception {
        PriorityGate gate = new PriorityGate();
        assertThat(maxConcurrent(gate, "SUBCONSCIOUS", 20)).isEqualTo(4);
        assertThat(maxConcurrent(gate, "MONITOR", 10)).isEqualTo(2);
        assertThat(gate.available(PriorityGate.GateClass.BACKGROUND)).isEqualTo(4);
    }

    private int maxConcurrent(PriorityGate gate, String module, int threads) throws Exception {
        AtomicInteger current = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        List<Thread> started = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            started.add(Thread.ofVirtual().start(() -> {
                try (PriorityGate.Permit ignored = gate.acquire(module)) {
                    int now = current.incrementAndGet();
                    max.accumulateAndGet(now, Math::max);
                    Thread.sleep(20);
                    current.decrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        for (Thread t : started) {
            t.join();
        }
        return max.get();
    }
}
