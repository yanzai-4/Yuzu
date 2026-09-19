package ai.yuzu.internal.consciousness;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.internal.subconscious.SubconsciousScheduler;

import java.util.List;

/**
 * v0.0.12 🍊 One agent's consciousness: its pool, its single main loop and its subconscious scheduler.
 *
 * <p>{@link #offer} is the only way messages enter the pool: the message is persisted (trace/recovery),
 * appended, a subconscious pass is scheduled for non-subconscious messages, and the main loop is woken.</p>
 */
public final class Consciousness {

    private final AgentId agentId;
    private final ConsciousnessPool pool;
    private final MainLoop mainLoop;
    private final SubconsciousScheduler subconscious;
    private final PoolMessageRepository repository;
    private final NaturalTime time;

    /** v0.0.12 🍊 Assembled by {@link ConsciousnessFactory}. */
    Consciousness(AgentId agentId, ConsciousnessPool pool, MainLoop mainLoop, SubconsciousScheduler subconscious,
                  PoolMessageRepository repository, NaturalTime time) {
        this.agentId = agentId;
        this.pool = pool;
        this.mainLoop = mainLoop;
        this.subconscious = subconscious;
        this.repository = repository;
        this.time = time;
    }

    /** v0.0.12 🍊 Persists and appends a message; wakes the main loop and (for triggers) the subconscious. */
    public PoolMessage offer(Origin origin, String attribution, String text, String traceId, int causalDepth,
                             boolean emittedByMain) {
        PoolMessage message = repository.insert(agentId, origin, attribution, text, traceId, causalDepth,
                emittedByMain, time.nowInstant());
        mainLoop.offer(message);
        return message;
    }

    /** v0.0.12 🍊 Pauses (no new main runs) or resumes. */
    public void setPaused(boolean paused) {
        mainLoop.setPaused(paused);
    }

    /** v0.0.12 🍊 The pool (sizes for the status board, diagnostics). */
    public ConsciousnessPool pool() {
        return pool;
    }

    /** v0.0.12 🍊 The subconscious scheduler (diagnostics). */
    public SubconsciousScheduler subconscious() {
        return subconscious;
    }

    /** v0.0.12 🍊 Marks a drained batch as consumed by a run (called by the main module). */
    public void markConsumed(List<PoolMessage> batch, String runId) {
        repository.markConsumed(agentId, batch.stream().map(PoolMessage::id).toList(), runId, time.nowInstant());
    }
}
