package ai.yuzu.agent.runtime;

import ai.yuzu.agent.AgentProfile;

/**
 * v0.0.15 🍊 A per-agent stateful component contributed by a feature package (for example the chat inbox).
 *
 * <p>Components live inside the agent's {@link AgentRuntime}, so their state is isolated per agent while
 * the feature's logic stays in shared, stateless beans.</p>
 */
public interface AgentComponent {

    /** v0.0.15 🍊 Called after the agent's profile changed (permissions, pause state, ...). */
    default void onProfileChanged(AgentProfile profile) {
    }

    /** v0.0.15 🍊 Called when the agent is retired or the server stops. */
    default void shutdown() {
    }
}
