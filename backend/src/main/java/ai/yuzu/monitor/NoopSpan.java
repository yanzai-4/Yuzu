package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;

import java.util.concurrent.atomic.AtomicBoolean;

/** v0.0.12 🍊 Span that reports nothing but still carries trace ids (monitor fallback and MonitorService.noop()). */
final class NoopSpan implements Span {

    private final AgentId agentId;
    private final String traceId;
    private final String spanId;
    private final AtomicBoolean finished = new AtomicBoolean();

    /** v0.0.12 🍊 Creates a detached span. */
    private NoopSpan(AgentId agentId, String traceId, String spanId) {
        this.agentId = agentId;
        this.traceId = traceId;
        this.spanId = spanId;
    }

    /** v0.0.12 🍊 Detached span keeping the given trace (or starting a new one) with a fresh span id. */
    static NoopSpan of(String traceId, AgentId agentId) {
        return new NoopSpan(agentId, TraceIds.cleanOrNew(traceId, agentId), TraceIds.newSpanId(agentId));
    }

    /** v0.0.12 🍊 Trace id. */
    @Override
    public String traceId() {
        return traceId;
    }

    /** v0.0.12 🍊 Span id. */
    @Override
    public String spanId() {
        return spanId;
    }

    /** v0.0.12 🍊 Ignored. */
    @Override
    public Span state(String text) {
        return this;
    }

    /** v0.0.12 🍊 Ignored. */
    @Override
    public Span desk(DeskState desk) {
        return this;
    }

    /** v0.0.12 🍊 Ignored. */
    @Override
    public Span detail(String key, Object value) {
        return this;
    }

    /** v0.0.12 🍊 Another detached span in the same trace. */
    @Override
    public Span child(ModuleKind module, String text) {
        return of(traceId, agentId);
    }

    /** v0.0.12 🍊 Marks the span finished. */
    @Override
    public void end(String text) {
        finished.set(true);
    }

    /** v0.0.12 🍊 Marks the span finished. */
    @Override
    public void fail(Throwable error) {
        finished.set(true);
    }

    /** v0.0.12 🍊 Marks the span finished. */
    @Override
    public void fail(String reason) {
        finished.set(true);
    }

    /** v0.0.12 🍊 Marks the span finished. */
    @Override
    public void cancelled(String reason) {
        finished.set(true);
    }

    /** v0.0.12 🍊 True once a terminal call was made. */
    @Override
    public boolean finished() {
        return finished.get();
    }

    /** v0.0.12 🍊 Marks the span finished. */
    @Override
    public void close() {
        finished.set(true);
    }
}
