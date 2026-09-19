package ai.yuzu.llm;

import ai.yuzu.common.error.CancelledException;
import ai.yuzu.llm.usage.TokenBudget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * v0.0.31 🍊 Global concurrency and budget gate for model calls, so 8 agents cannot flood the API.
 *
 * <p>MAIN_TOOL 8, CHAT 8, REVIEW 8, BACKGROUND 4, MONITOR 2 permits. After a 429 the BACKGROUND class is
 * halved for 30 seconds (background work yields to the main consciousness and chat). Before any permit is
 * taken the {@link TokenBudget} is consulted: once the configured token or cost ceiling is reached every
 * acquisition is refused with {@code BudgetExhaustedException}, which each module turns into its own
 * graceful degradation.</p>
 */
@Component
public class PriorityGate {

    /** v0.0.11 🍊 Priority classes and their permit counts. */
    public enum GateClass {
        MAIN_TOOL(8), CHAT(8), REVIEW(8), BACKGROUND(4), MONITOR(2);

        private final int permits;

        GateClass(int permits) {
            this.permits = permits;
        }

        /** v0.0.11 🍊 Default permits of the class. */
        public int permits() {
            return permits;
        }

        /** v0.0.11 🍊 Class of a module name. */
        public static GateClass of(String module) {
            return switch (module == null ? "" : module) {
                case "MAIN", "TOOL", "TOOL_CALLING" -> MAIN_TOOL;
                case "CHAT" -> CHAT;
                case "SUBCONSCIOUS", "LEARNING", "MEMORY", "WM_COMPACTOR" -> BACKGROUND;
                case "MONITOR" -> MONITOR;
                default -> REVIEW;
            };
        }
    }

    private final Map<GateClass, Semaphore> semaphores = new EnumMap<>(GateClass.class);
    private final AtomicBoolean throttled = new AtomicBoolean();
    private final TokenBudget budget;

    /** v0.0.31 🍊 Creates one fair semaphore per class, with no budget (unit tests and probes). */
    public PriorityGate() {
        this(null);
    }

    /** v0.0.31 🍊 Creates one fair semaphore per class, guarded by the token/cost budget. */
    @Autowired
    public PriorityGate(TokenBudget budget) {
        this.budget = budget;
        for (GateClass c : GateClass.values()) {
            semaphores.put(c, new Semaphore(c.permits(), true));
        }
    }

    /**
     * v0.0.31 🍊 Refuses the call when the budget is exhausted, otherwise blocks (interruptibly) until the
     * module's class has a free permit; returns a releaser.
     */
    public Permit acquire(String module) {
        if (budget != null) {
            budget.checkAvailable();
        }
        GateClass gateClass = GateClass.of(module);
        Semaphore semaphore = semaphores.get(gateClass);
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancelledException("cancelled while waiting for a model slot");
        }
        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true)) {
                semaphore.release();
            }
        };
    }

    /** v0.0.11 🍊 Called on HTTP 429: halves BACKGROUND capacity for 30 seconds (once at a time). */
    public void onRateLimited() {
        if (!throttled.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            Semaphore background = semaphores.get(GateClass.BACKGROUND);
            int taken = 0;
            try {
                int wanted = GateClass.BACKGROUND.permits() / 2;
                while (taken < wanted && background.tryAcquire(5, TimeUnit.SECONDS)) {
                    taken++;
                }
                Thread.sleep(30_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                background.release(taken);
                throttled.set(false);
            }
        });
    }

    /** v0.0.11 🍊 Currently free permits of a class (tests, diagnostics). */
    public int available(GateClass gateClass) {
        return semaphores.get(gateClass).availablePermits();
    }

    /** v0.0.11 🍊 Releases the permit (idempotent). */
    @FunctionalInterface
    public interface Permit extends AutoCloseable {
        /** v0.0.11 🍊 Releases the permit. */
        @Override
        void close();
    }
}
