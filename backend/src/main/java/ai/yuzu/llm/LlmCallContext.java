package ai.yuzu.llm;

import ai.yuzu.common.concurrent.CancelToken;
import ai.yuzu.common.id.AgentId;

/**
 * v0.0.11 🍊 Who is calling the model and why (used for tiers, gating, metering, tracing and cache keys).
 *
 * @param agentId agent the call belongs to ({@link AgentId#SYSTEM} for platform calls such as the probe)
 * @param module  module name (for example "CHAT", "MAIN", "SAFETY")
 * @param tier    model tier configured in the console
 * @param traceId trace the call belongs to (may be null)
 * @param cancel  cancellation token of the agent's current work
 */
public record LlmCallContext(AgentId agentId, String module, ModelTier tier, String traceId, CancelToken cancel) {

    /** v0.0.11 🍊 Provider-side prompt cache routing key: stable per module and agent. */
    public String promptCacheKey() {
        return "yuzu:" + module + ":" + agentId.value();
    }
}
