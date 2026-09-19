package ai.yuzu.tool.spi;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.module.ModuleSpan;

/**
 * v0.0.18 🍊 Everything a tool may use while executing one call.
 *
 * @param instruction the natural-language action this call implements
 * @param causalDepth agent-hop depth inherited from the triggering input (for chat posts)
 */
public record ToolContext(AgentContext agent, String batchId, String toolCallId, int actionIndex, String instruction,
                          int causalDepth, ModuleSpan span) {

    /** v0.0.18 🍊 The agent's profile (permissions are always taken from here, never from model output). */
    public AgentProfile profile() {
        return agent.profile();
    }
}
