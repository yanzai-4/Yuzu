package ai.yuzu.internal.planning;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.internal.intake.Stimulus;

/**
 * v0.0.16 🍊 The planning half of "planning/cognition": updates the agent's task list for a stimulus.
 */
public interface TaskPlanner {

    /** v0.0.16 🍊 Applies task-list changes; returns a first-person note about them, or null when nothing changed. */
    String plan(AgentContext ctx, Stimulus stimulus, String stimulusText);
}
