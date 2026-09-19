package ai.yuzu.task.list;

import ai.yuzu.common.error.CancelledException;
import ai.yuzu.common.error.ConflictException;
import ai.yuzu.common.id.AgentId;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** v0.0.10 🍊 One ReentrantLock per agent with a bounded, interruptible wait (never synchronized: virtual threads). */
final class AgentLocks {

    private final Map<AgentId, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final long timeoutMillis;

    /** v0.0.10 🍊 Creates the lock table with the maximum time a caller waits for an agent's lock. */
    AgentLocks(Duration timeout) {
        this.timeoutMillis = timeout.toMillis();
    }

    /** v0.0.10 🍊 Runs the action while holding the agent's lock; CONFLICT on timeout, CANCELLED when interrupted. */
    <T> T withLock(AgentId agentId, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(agentId, id -> new ReentrantLock());
        boolean acquired;
        try {
            acquired = lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancelledException("Interrupted while waiting for the task list of " + agentId + ".", e);
        }
        if (!acquired) {
            throw new ConflictException("The task list of " + agentId + " is busy; please retry in a moment.")
                    .forAgent(agentId.value());
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
