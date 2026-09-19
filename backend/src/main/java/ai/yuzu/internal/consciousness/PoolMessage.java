package ai.yuzu.internal.consciousness;

import ai.yuzu.common.id.AgentId;

import java.time.Instant;

/**
 * v0.0.12 🍊 One message in an agent's consciousness pool.
 *
 * @param attribution   first-person source line built by code ("Alice (human) told me in the group chat at ...")
 * @param causalDepth   agent-hop depth inherited from the triggering chat message (loop guard)
 * @param emittedByMain true for the main consciousness's own THINK message (already stored as its output, so
 *                      working memory must not store it again as an input)
 */
public record PoolMessage(String id, AgentId agentId, Origin origin, String attribution, String text,
                          String traceId, int causalDepth, boolean emittedByMain, Instant createdAt) {

    /** v0.0.12 🍊 True when this message may start a main run. */
    public boolean isTrigger() {
        return origin.triggers();
    }
}
