package ai.yuzu.external.chat;

import ai.yuzu.agent.runtime.AgentContext;

/** v0.0.15 🍊 Receives forwarded chat (safety review → planning/cognition → pool); implemented by the intake. */
public interface ChatForwarder {

    /** v0.0.15 🍊 Forwards chat to the agent's internal modules. */
    void forward(AgentContext ctx, ChatForward forward);
}
