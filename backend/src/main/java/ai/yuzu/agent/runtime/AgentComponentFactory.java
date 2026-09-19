package ai.yuzu.agent.runtime;

import ai.yuzu.agent.AgentProfile;

/** v0.0.15 🍊 Creates one {@link AgentComponent} per agent runtime (registered as a Spring bean). */
public interface AgentComponentFactory<T extends AgentComponent> {

    /** v0.0.15 🍊 The component type (lookup key inside the runtime). */
    Class<T> type();

    /** v0.0.15 🍊 Creates the component for a new runtime. */
    T create(AgentProfile profile);
}
