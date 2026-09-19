package ai.yuzu.agent.runtime;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.common.concurrent.CancelToken;
import ai.yuzu.common.id.AgentId;

import java.time.Instant;

/**
 * v0.0.14 🍊 Immutable snapshot handed to a module invocation: who, which trace, when, and how to cancel.
 *
 * <p>The current time is captured once per stimulus so every prompt of the invocation (including retries)
 * renders the same "Current time" line.</p>
 *
 * @param parentSpanId monitor span that caused this work (null at the root of a trace)
 */
public record AgentContext(AgentId agentId, String roomId, AgentProfile profile, String traceId, String parentSpanId,
                           CancelToken cancel, Instant now, String nowText) {

    /** v0.0.14 🍊 Copy whose parent span is the given span (for nested module calls). */
    public AgentContext withParent(String spanId) {
        return new AgentContext(agentId, roomId, profile, traceId, spanId, cancel, now, nowText);
    }

    /** v0.0.14 🍊 Copy with a fresh time (a new step of the same trace). */
    public AgentContext at(Instant newNow, String newNowText) {
        return new AgentContext(agentId, roomId, profile, traceId, parentSpanId, cancel, newNow, newNowText);
    }
}
