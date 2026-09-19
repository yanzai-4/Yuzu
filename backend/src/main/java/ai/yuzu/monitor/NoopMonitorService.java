package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;

import java.util.Map;

/** v0.0.12 🍊 MonitorService that reports nothing; its spans still carry trace ids so propagation keeps working. */
final class NoopMonitorService implements MonitorService {

    /** v0.0.12 🍊 Shared instance returned by {@link MonitorService#noop()}. */
    static final NoopMonitorService INSTANCE = new NoopMonitorService();

    /** v0.0.12 🍊 Singleton. */
    private NoopMonitorService() {
    }

    /** v0.0.12 🍊 A detached span in the given (or a new) trace. */
    @Override
    public Span start(AgentId agentId, ModuleKind module, String text, String traceId, String parentSpanId) {
        return NoopSpan.of(traceId, agentId);
    }

    /** v0.0.12 🍊 Ignored. */
    @Override
    public void info(AgentId agentId, ModuleKind module, String text, Map<String, ?> detail, String traceId,
                     String parentSpanId) {
    }

    /** v0.0.12 🍊 Ignored. */
    @Override
    public void setPoolSize(AgentId agentId, int size) {
    }

    /** v0.0.12 🍊 Ignored. */
    @Override
    public void setPendingBatches(AgentId agentId, int count) {
    }

    /** v0.0.12 🍊 Ignored. */
    @Override
    public void setWaiting(AgentId agentId, boolean waiting, String reason) {
    }

    /** v0.0.12 🍊 Ignored. */
    @Override
    public void setPaused(AgentId agentId, boolean paused) {
    }
}
