package ai.yuzu.internal.consciousness;

import ai.yuzu.common.id.AgentId;

import java.util.List;

/** v0.0.12 🍊 Runs ONE main-consciousness step over a drained batch (implemented by the main module). */
@FunctionalInterface
public interface MainRunHandler {

    /** v0.0.12 🍊 Handles a non-empty batch; may append SELF messages to keep thinking. */
    void runOnce(AgentId agentId, List<PoolMessage> batch);
}
