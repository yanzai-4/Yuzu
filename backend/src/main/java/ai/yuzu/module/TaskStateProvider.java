package ai.yuzu.module;

import ai.yuzu.common.id.AgentId;

/**
 * v0.0.15 🍊 Supplies the task-list text modules put into their prompts (implemented by the task package).
 */
public interface TaskStateProvider {

    /** v0.0.15 🍊 The agent's current task list with progress ("(no task list)" when none). */
    String current(AgentId agentId);

    /** v0.0.15 🍊 The last 3 archived task lists and how they went. */
    String history(AgentId agentId);

    /** v0.0.15 🍊 Fallback used until the task package registers its provider. */
    TaskStateProvider NONE = new TaskStateProvider() {
        @Override
        public String current(AgentId agentId) {
            return "(no task list)";
        }

        @Override
        public String history(AgentId agentId) {
            return "(no archived task lists)";
        }
    };
}
