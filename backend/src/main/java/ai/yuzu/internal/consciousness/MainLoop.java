package ai.yuzu.internal.consciousness;

import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.id.AgentId;

import java.util.List;
import java.util.function.Consumer;

/**
 * v0.0.12 🍊 Drives an agent's single-threaded main consciousness over its {@link ConsciousnessPool}.
 *
 * <p>{@link #offer} appends a message and, when it becomes the owner, starts ONE virtual thread that keeps
 * draining the pool until nothing can trigger a run. A THINK step simply appends a SELF message during its
 * run; the loop picks it up next. If a run throws, the error is reported and the loop restarts only when
 * triggers are waiting. Subconscious dispatch is delegated to the {@code onTrigger} hook, which is called
 * for every non-subconscious message ("a new subconscious thread per new pool message").</p>
 */
public final class MainLoop {

    private final AgentId agentId;
    private final ConsciousnessPool pool;
    private final MainRunHandler handler;
    private final AsyncRunner runner;
    private final Consumer<PoolMessage> onTrigger;

    /** v0.0.12 🍊 Creates the loop of one agent. */
    public MainLoop(AgentId agentId, ConsciousnessPool pool, MainRunHandler handler, AsyncRunner runner,
                    Consumer<PoolMessage> onTrigger) {
        this.agentId = agentId;
        this.pool = pool;
        this.handler = handler;
        this.runner = runner;
        this.onTrigger = onTrigger;
    }

    /** v0.0.12 🍊 Adds a message to the pool, dispatches the subconscious for triggers, and wakes the loop. */
    public void offer(PoolMessage message) {
        boolean start = pool.append(message);
        if (message.isTrigger() && onTrigger != null) {
            onTrigger.accept(message);
        }
        if (start) {
            startLoop();
        }
    }

    /** v0.0.12 🍊 Pauses (the current run finishes, no new run starts) or resumes. */
    public void setPaused(boolean paused) {
        if (pool.setPaused(paused)) {
            startLoop();
        }
    }

    /** v0.0.12 🍊 The pool (read-only use by status reporting). */
    public ConsciousnessPool pool() {
        return pool;
    }

    /** v0.0.12 🍊 Starts the owner loop on a virtual thread. */
    private void startLoop() {
        runner.run("main-loop", agentId.value(), this::loop);
    }

    /** v0.0.12 🍊 Drains batches until the pool releases ownership; restarts after a crash if needed. */
    private void loop() {
        try {
            for (List<PoolMessage> batch = pool.takeAllOrRelease(); !batch.isEmpty();
                 batch = pool.takeAllOrRelease()) {
                handler.runOnce(agentId, batch);
            }
        } catch (RuntimeException | Error e) {
            if (pool.abandon()) {
                startLoop();
            }
            throw e;
        }
    }
}
