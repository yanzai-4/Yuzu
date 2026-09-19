package ai.yuzu.agent;

/** v0.0.6 🍊 Notified after an agent is created, edited, paused/resumed or retired (runtime manager, caches). */
public interface AgentLifecycleListener {

    /** v0.0.6 🍊 A new agent was hired. */
    default void onCreated(AgentProfile profile) {
    }

    /** v0.0.6 🍊 An agent's profile, permissions or state changed. */
    default void onUpdated(AgentProfile profile) {
    }

    /** v0.0.6 🍊 An agent was retired. */
    default void onRetired(AgentProfile profile) {
    }
}
