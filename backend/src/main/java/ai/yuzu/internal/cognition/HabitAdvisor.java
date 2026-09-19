package ai.yuzu.internal.cognition;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.internal.intake.Stimulus;

/**
 * v0.0.16 🍊 The cognition half of "planning/cognition": recalls habits (techniques) that fit the stimulus.
 */
public interface HabitAdvisor {

    /** v0.0.16 🍊 First-person note about habits that apply ("I recalled my habit ..."), or null when none. */
    String advise(AgentContext ctx, Stimulus stimulus, String stimulusText);
}
