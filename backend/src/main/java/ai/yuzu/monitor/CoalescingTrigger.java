package ai.yuzu.monitor;

import ai.yuzu.common.concurrent.AsyncRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** v0.0.12 🍊 Throttle: bursts of fire() become at most one run per interval (leading + trailing, latest state wins, never overlapping). */
final class CoalescingTrigger {

    private static final Logger log = LoggerFactory.getLogger(CoalescingTrigger.class);

    private final String context;
    private final String agentId;
    private final long intervalNanos;
    private final ScheduledExecutorService timer;
    private final AsyncRunner runner;
    private final Runnable action;
    private final ReentrantLock lock = new ReentrantLock();
    private boolean pending;
    private boolean dirty;
    private boolean ran;
    private long lastStart;
    private boolean closed;

    /** v0.0.12 🍊 Creates a trigger running {@code action} at most once per {@code interval}. */
    CoalescingTrigger(String context, String agentId, Duration interval, ScheduledExecutorService timer,
                      AsyncRunner runner, Runnable action) {
        this.context = context;
        this.agentId = agentId;
        this.intervalNanos = interval.toNanos();
        this.timer = timer;
        this.runner = runner;
        this.action = action;
    }

    /** v0.0.12 🍊 Requests a run without blocking: now if the interval has passed, otherwise at its end. */
    void fire() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            dirty = true;
            if (!pending) {
                pending = true;
                scheduleLocked();
            }
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Stops future runs (the agent was retired). */
    void close() {
        lock.lock();
        try {
            closed = true;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Schedules the next run respecting the interval since the previous start (lock held). */
    private void scheduleLocked() {
        long wait = ran ? lastStart + intervalNanos - System.nanoTime() : 0;
        try {
            if (wait <= 0) {
                runner.run(context, agentId, this::runOnce);
            } else {
                timer.schedule(this::handOff, wait, TimeUnit.NANOSECONDS);
            }
        } catch (RejectedExecutionException e) {
            pending = false;
            log.debug("Monitor task {} for {} not scheduled (shutting down)", context, agentId);
        }
    }

    /** v0.0.12 🍊 Timer callback: moves the run to a virtual thread so the timer thread never blocks. */
    private void handOff() {
        try {
            runner.run(context, agentId, this::runOnce);
        } catch (RejectedExecutionException e) {
            lock.lock();
            try {
                pending = false;
            } finally {
                lock.unlock();
            }
        }
    }

    /** v0.0.12 🍊 Runs the action once, then schedules a trailing run if fire() was called meanwhile. */
    private void runOnce() {
        lock.lock();
        try {
            if (closed) {
                pending = false;
                return;
            }
            dirty = false;
            ran = true;
            lastStart = System.nanoTime();
        } finally {
            lock.unlock();
        }
        try {
            action.run();
        } catch (Throwable t) {
            log.warn("Monitor task {} failed for {}", context, agentId, t);
        } finally {
            lock.lock();
            try {
                if (dirty && !closed) {
                    scheduleLocked();
                } else {
                    pending = false;
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
