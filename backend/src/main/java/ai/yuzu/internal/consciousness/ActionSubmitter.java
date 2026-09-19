package ai.yuzu.internal.consciousness;

import ai.yuzu.agent.runtime.AgentContext;

import java.util.List;

/** v0.0.17 🍊 Hands the main consciousness's actions to the action pipeline (behavior review → tool calling). */
public interface ActionSubmitter {

    /** v0.0.17 🍊 Submits a batch of natural-language actions decided in a main run (asynchronous). */
    void submit(AgentContext ctx, String runId, List<String> actions, int causalDepth);

    /** v0.0.17 🍊 Actions still being reviewed or executed, rendered for the main consciousness. */
    String inProgress(AgentContext ctx);
}
