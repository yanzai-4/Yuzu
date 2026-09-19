package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;

import java.util.Map;

/** v0.0.12 🍊 The single reporting API of every agent module: spans, one-off events and live counters; non-blocking, never throws. */
public interface MonitorService {

    /** v0.0.12 🍊 Starts a span and reports START; a null traceId starts a new trace, parentSpanId links nested work. */
    Span start(AgentId agentId, ModuleKind module, String text, String traceId, String parentSpanId);

    /** v0.0.12 🍊 Starts a root span in a new trace. */
    default Span start(AgentId agentId, ModuleKind module, String text) {
        return start(agentId, module, text, null, null);
    }

    /** v0.0.12 🍊 Reports a one-off INFO event outside any trace. */
    default void info(AgentId agentId, ModuleKind module, String text, Map<String, ?> detail) {
        info(agentId, module, text, detail, null, null);
    }

    /** v0.0.12 🍊 Reports a one-off INFO event inside a trace (traceId and parentSpanId may be null). */
    void info(AgentId agentId, ModuleKind module, String text, Map<String, ?> detail, String traceId,
              String parentSpanId);

    /** v0.0.12 🍊 Number of messages waiting in the agent's consciousness pool. */
    void setPoolSize(AgentId agentId, int size);

    /** v0.0.12 🍊 Number of action batches still being reviewed or executed. */
    void setPendingBatches(AgentId agentId, int count);

    /** v0.0.12 🍊 Whether the agent waits on a human (question or approval card) and why (shown in the bubble). */
    void setWaiting(AgentId agentId, boolean waiting, String reason);

    /** v0.0.12 🍊 Whether the agent is paused (the agent lifecycle listener also keeps this in sync). */
    void setPaused(AgentId agentId, boolean paused);

    /** v0.0.12 🍊 Monitor that reports nothing (spans still carry trace ids): for unit tests of other modules. */
    static MonitorService noop() {
        return NoopMonitorService.INSTANCE;
    }
}
