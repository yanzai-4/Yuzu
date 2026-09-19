package ai.yuzu.llm;

import ai.yuzu.common.error.BudgetExhaustedException;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.usage.BudgetProperties;
import ai.yuzu.llm.usage.TokenBudget;
import ai.yuzu.llm.usage.TokenMeter;
import ai.yuzu.llm.usage.Usage;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * v0.0.31 🍊 The token/cost budget pauses new model calls instead of letting 8 agents hammer the provider.
 *
 * <p>The budget reads what {@link TokenMeter} already counts, so nothing is metered twice. It trips once,
 * announces itself on the realtime stream once, and refuses every {@link PriorityGate} acquisition until a
 * human raises the limit or resumes.</p>
 */
class TokenBudgetTest {

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final NaturalTime time = new NaturalTime(Clock.systemUTC(), ZoneId.of("America/Los_Angeles"));
    private final SseHub hub = new SseHub(new ObjectMapper(), time, timer);
    private final TokenMeter meter = new TokenMeter(hub, time, timer);

    @AfterEach
    void tearDown() {
        meter.stop();
        hub.shutdown();
        timer.shutdownNow();
    }

    /** v0.0.31 🍊 With no configured budget nothing ever pauses (the default for the demo). */
    @Test
    void unlimitedByDefault() {
        TokenBudget budget = budget(new BudgetProperties(0, null, null));
        spend(1_000_000);
        assertThatCode(budget::checkAvailable).doesNotThrowAnyException();
        assertThat(budget.status().paused()).isFalse();
        assertThat(budget.status().maxTotalTokens()).isZero();
    }

    /** v0.0.31 🍊 Under the token budget calls pass; at the budget the next call is refused. */
    @Test
    void tokenBudgetPausesNewCalls() {
        TokenBudget budget = budget(new BudgetProperties(10_000, null, null));
        spend(9_000);
        assertThatCode(budget::checkAvailable).doesNotThrowAnyException();
        spend(1_500);
        assertThatThrownBy(budget::checkAvailable).isInstanceOf(BudgetExhaustedException.class)
                .hasMessageContaining("token budget");
        TokenBudget.Status status = budget.status();
        assertThat(status.paused()).isTrue();
        assertThat(status.usedTokens()).isEqualTo(10_500);
        assertThat(status.maxTotalTokens()).isEqualTo(10_000);
        assertThat(status.reason()).contains("token budget");
    }

    /** v0.0.31 🍊 A cost ceiling works the same way, priced from the configured blended rate. */
    @Test
    void costBudgetPausesNewCalls() {
        TokenBudget budget = budget(new BudgetProperties(0, new BigDecimal("0.50"), new BigDecimal("2.00")));
        spend(200_000);
        assertThatCode(budget::checkAvailable).doesNotThrowAnyException();
        spend(60_000);
        assertThatThrownBy(budget::checkAvailable).isInstanceOf(BudgetExhaustedException.class)
                .hasMessageContaining("cost budget");
        assertThat(budget.status().usedUsd()).isEqualTo("0.52");
        assertThat(budget.status().maxCostUsd()).isEqualTo("0.50");
    }

    /** v0.0.31 🍊 The pause is announced exactly once, however many agents run into it. */
    @Test
    void pauseIsAnnouncedOnceOnTheRealtimeStream() throws Exception {
        TokenBudget budget = budget(new BudgetProperties(100, null, null));
        spend(500);
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger refused = new AtomicInteger();
        List<Thread> callers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            callers.add(Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    budget.checkAvailable();
                } catch (BudgetExhaustedException e) {
                    refused.incrementAndGet();
                }
            }));
        }
        start.countDown();
        for (Thread t : callers) {
            t.join();
        }
        assertThat(refused.get()).isEqualTo(threads);
        assertThat(hub.bufferedEvents().stream().filter(e -> e.type() == EventType.ERROR).count()).isEqualTo(1);
    }

    /** v0.0.31 🍊 An exhausted budget refuses a gate permit, so the model layer never even queues the call. */
    @Test
    void gateRefusesPermitsWhileExhausted() {
        TokenBudget budget = budget(new BudgetProperties(100, null, null));
        PriorityGate gate = new PriorityGate(budget);
        try (PriorityGate.Permit ignored = gate.acquire("MAIN")) {
            assertThat(gate.available(PriorityGate.GateClass.MAIN_TOOL))
                    .isEqualTo(PriorityGate.GateClass.MAIN_TOOL.permits() - 1);
        }
        spend(500);
        assertThatThrownBy(() -> gate.acquire("MAIN")).isInstanceOf(BudgetExhaustedException.class);
        assertThatThrownBy(() -> gate.acquire("CHAT")).isInstanceOf(BudgetExhaustedException.class);
        assertThat(gate.available(PriorityGate.GateClass.MAIN_TOOL))
                .as("a refused call must not hold a permit").isEqualTo(PriorityGate.GateClass.MAIN_TOOL.permits());
    }

    /** v0.0.31 🍊 A gate built without a budget (unit tests, probes) behaves exactly as before. */
    @Test
    void gateWithoutABudgetIsUnchanged() {
        PriorityGate gate = new PriorityGate();
        try (PriorityGate.Permit ignored = gate.acquire("MAIN")) {
            assertThat(gate.available(PriorityGate.GateClass.MAIN_TOOL))
                    .isEqualTo(PriorityGate.GateClass.MAIN_TOOL.permits() - 1);
        }
    }

    /** v0.0.31 🍊 Raising the limit lets the agents continue; resuming below the limit works too. */
    @Test
    void raisingTheLimitResumesWork() {
        TokenBudget budget = budget(new BudgetProperties(100, null, null));
        spend(500);
        assertThatThrownBy(budget::checkAvailable).isInstanceOf(BudgetExhaustedException.class);

        assertThat(budget.resume()).as("resuming without headroom must fail").isFalse();
        budget.setLimits(10_000, null);
        assertThat(budget.status().paused()).isFalse();
        assertThatCode(budget::checkAvailable).doesNotThrowAnyException();

        spend(20_000);
        assertThatThrownBy(budget::checkAvailable).isInstanceOf(BudgetExhaustedException.class);
        budget.setLimits(0, null);
        assertThatCode(budget::checkAvailable).doesNotThrowAnyException();
        assertThat(budget.resume()).isTrue();
    }

    /** v0.0.31 🍊 Budget for a configuration, wired to the shared meter and hub. */
    private TokenBudget budget(BudgetProperties properties) {
        return new TokenBudget(meter, properties, hub, time);
    }

    /** v0.0.31 🍊 Records billable tokens through the normal metering path. */
    private void spend(int tokens) {
        meter.recordAttempt(new TokenMeter.Key("agent-3fa9", "MAIN", "DEFAULT", "test-model"),
                new Usage(tokens - tokens / 2, 0, 0, tokens / 2, 0, false, true), 12, false);
    }
}
