package ai.yuzu.task.list;

import ai.yuzu.common.time.NaturalTime;

import java.time.Instant;

/** v0.0.10 🍊 One goal edit kept in task_list.goal_history (previous goal, reason, UTC time) so no goal is ever lost. */
public record GoalChange(String previousGoal, String reason, Instant changedAt) {

    /** v0.0.10 🍊 View with a natural-language time. */
    public PreviousGoal toView(NaturalTime time) {
        return new PreviousGoal(previousGoal, reason, time.compact(changedAt));
    }
}
