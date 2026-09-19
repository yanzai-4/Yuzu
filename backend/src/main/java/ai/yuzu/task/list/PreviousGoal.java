package ai.yuzu.task.list;

/** v0.0.10 🍊 A replaced goal with its reason and natural-language time (Java-only part of {@link TaskList}). */
public record PreviousGoal(String goal, String reason, String time) {
}
