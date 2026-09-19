package ai.yuzu.internal.subconscious;

import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.internal.consciousness.PoolMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * v0.0.12 🍊 Runs an agent's subconscious threads: one per new non-subconscious pool message, bounded.
 *
 * <p>At most {@value #MAX_CONCURRENT} subconscious threads run per agent. While they are busy, newly
 * arriving messages are coalesced into ONE waiting batch (never dropped), so a burst costs one extra call
 * instead of one per message. Each thread only sees its own new messages ("current round only").</p>
 */
public final class SubconsciousScheduler {

    /** v0.0.12 🍊 Concurrent subconscious threads per agent. */
    public static final int MAX_CONCURRENT = 2;

    private final AgentId agentId;
    private final SubconsciousHandler handler;
    private final AsyncRunner runner;
    private final Semaphore permits = new Semaphore(MAX_CONCURRENT);
    private final ReentrantLock lock = new ReentrantLock();
    private List<PoolMessage> pending = new ArrayList<>();
    private boolean drainQueued;

    /** v0.0.12 🍊 Creates the scheduler of one agent. */
    public SubconsciousScheduler(AgentId agentId, SubconsciousHandler handler, AsyncRunner runner) {
        this.agentId = agentId;
        this.handler = handler;
        this.runner = runner;
    }

    /** v0.0.12 🍊 Schedules subconscious processing of a new pool message (ignores subconscious messages). */
    public void onNew(PoolMessage message) {
        if (!message.isTrigger()) {
            return;
        }
        lock.lock();
        try {
            pending.add(message);
            if (drainQueued) {
                return;
            }
            drainQueued = true;
        } finally {
            lock.unlock();
        }
        runner.run("subconscious", agentId.value(), this::drain);
    }

    /** v0.0.12 🍊 Waits for a permit, takes the waiting batch and runs one subconscious thread over it. */
    private void drain() {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lock.lock();
            try {
                drainQueued = false;
            } finally {
                lock.unlock();
            }
            return;
        }
        List<PoolMessage> batch;
        lock.lock();
        try {
            batch = pending;
            pending = new ArrayList<>();
            drainQueued = false;
        } finally {
            lock.unlock();
        }
        try {
            if (!batch.isEmpty()) {
                handler.run(agentId, List.copyOf(batch));
            }
        } finally {
            permits.release();
        }
    }

    /** v0.0.12 🍊 Free permits (tests, diagnostics). */
    public int availablePermits() {
        return permits.availablePermits();
    }
}
