package ai.yuzu.module;

import ai.yuzu.common.id.AgentId;

/**
 * v0.0.14 🍊 How modules report to the monitor. Decouples the module base class from the monitor implementation.
 */
public interface ModuleReporter {

    /** v0.0.14 🍊 Starts a span for a module of an agent. */
    ModuleSpan start(AgentId agentId, String module, String text, String traceId, String parentSpanId);
}
