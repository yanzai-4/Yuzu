package ai.yuzu.common.concurrent;

import ai.yuzu.common.error.CancelledException;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v0.0.1 🍊 Cooperative cancellation handle shared by an agent's in-flight work.
 *
 * <p>Threads bind themselves while working; {@link #cancel(String)} interrupts them, which also aborts
 * blocking {@code HttpClient.send} calls (JDK 16+). Code checks {@link #throwIfCancelled()} at safe points.</p>
 */
public final class CancelToken {

    private final Set<Thread> bound = ConcurrentHashMap.newKeySet();
    private volatile boolean cancelled;
    private volatile String reason = "cancelled";

    /** v0.0.1 🍊 Marks the token cancelled and interrupts every bound thread. */
    public void cancel(String why) {
        this.reason = why == null ? "cancelled" : why;
        this.cancelled = true;
        bound.forEach(Thread::interrupt);
    }

    /** v0.0.1 🍊 True once {@link #cancel(String)} was called. */
    public boolean isCancelled() {
        return cancelled;
    }

    /** v0.0.1 🍊 The reason given to {@link #cancel(String)}. */
    public String reason() {
        return reason;
    }

    /** v0.0.1 🍊 Throws {@link CancelledException} if cancelled or if the current thread was interrupted. */
    public void throwIfCancelled() {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            throw new CancelledException(reason);
        }
    }

    /** v0.0.1 🍊 Binds the current thread until the returned handle is closed (try-with-resources). */
    public Binding bindCurrentThread() {
        Thread current = Thread.currentThread();
        bound.add(current);
        if (cancelled) {
            current.interrupt();
        }
        return () -> bound.remove(current);
    }

    /** v0.0.1 🍊 Handle returned by {@link #bindCurrentThread()}; closing it unbinds the thread. */
    @FunctionalInterface
    public interface Binding extends AutoCloseable {
        /** v0.0.1 🍊 Unbinds the thread (never throws). */
        @Override
        void close();
    }
}
