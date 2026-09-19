package ai.yuzu.task.list;

/** v0.0.20 🍊 API shape of a task item (contract type {@code TaskItem}); ord is its stable 1-based number. */
public record TaskItem(String id, int ord, String text, TaskItemState state, String note, String struckReason) {
}
