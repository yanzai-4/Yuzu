package ai.yuzu.internal.consciousness;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * v0.0.12 🍊 The main consciousness's message pool with exactly-one-run ownership and no lost wake-ups.
 *
 * <p>State guarded by one short lock: the queue, the number of queued trigger messages (non-subconscious),
 * the {@code running} ownership flag and {@code paused}.</p>
 * <ul>
 *   <li>{@link #append} returns true iff the caller must start the run loop (nobody owns it, not paused, and
 *       at least one trigger is queued). A pool holding only subconscious messages never starts a run.</li>
 *   <li>{@link #takeAllOrRelease} hands the whole pool (subconscious messages ride along) to the owner, or —
 *       in the SAME critical section — releases ownership when there is nothing to trigger a run. Because
 *       release and the trigger check are atomic with respect to {@code append}, a message can never be
 *       stranded: either the running loop sees it, or the appender becomes the new owner.</li>
 * </ul>
 */
public final class ConsciousnessPool {

    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayDeque<PoolMessage> queue = new ArrayDeque<>();
    private int triggers;
    private boolean running;
    private boolean paused;

    /** v0.0.12 🍊 Adds a message; returns true when the caller must start the run loop. */
    public boolean append(PoolMessage message) {
        lock.lock();
        try {
            queue.addLast(message);
            if (message.isTrigger()) {
                triggers++;
            }
            if (!running && !paused && triggers > 0) {
                running = true;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Takes the whole pool if a run is warranted; otherwise atomically gives up ownership. */
    public List<PoolMessage> takeAllOrRelease() {
        lock.lock();
        try {
            if (paused || triggers == 0) {
                running = false;
                return List.of();
            }
            List<PoolMessage> batch = List.copyOf(queue);
            queue.clear();
            triggers = 0;
            return batch;
        } finally {
            lock.unlock();
        }
    }

    /**
     * v0.0.12 🍊 Called when the loop dies unexpectedly: keeps ownership (returns true) if more triggers are
     * waiting so the caller restarts the loop, otherwise releases it.
     */
    public boolean abandon() {
        lock.lock();
        try {
            if (!paused && triggers > 0) {
                running = true;
                return true;
            }
            running = false;
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Pauses or resumes; returns true when resuming requires the caller to start the loop. */
    public boolean setPaused(boolean value) {
        lock.lock();
        try {
            paused = value;
            if (!paused && !running && triggers > 0) {
                running = true;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Number of queued messages. */
    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Number of queued trigger (non-subconscious) messages. */
    public int triggerCount() {
        lock.lock();
        try {
            return triggers;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 True while a run loop owns the pool. */
    public boolean isRunning() {
        lock.lock();
        try {
            return running;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Copy of the queued messages (diagnostics, never used to consume). */
    public List<PoolMessage> peek() {
        lock.lock();
        try {
            return List.copyOf(queue);
        } finally {
            lock.unlock();
        }
    }
}
