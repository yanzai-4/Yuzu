package ai.yuzu.task.list;

import ai.yuzu.task.Actor;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Optional;

/** v0.0.10 🍊 API shape of a task list (contract {@code TaskList}) plus Java-only publisherKind, previousGoals. */
public record TaskList(String id, String agentId, String goal, TaskListStatus status, String publisherId,
                       String publisherName, String ticketId, String outcome, List<TaskItem> items, String time,
                       String archivedTime, @JsonIgnore Actor.Kind publisherKind,
                       @JsonIgnore List<PreviousGoal> previousGoals) {

    /** v0.0.10 🍊 Defensive, immutable copies of the item and goal lists. */
    public TaskList {
        items = items == null ? List.of() : List.copyOf(items);
        previousGoals = previousGoals == null ? List.of() : List.copyOf(previousGoals);
    }

    /** v0.0.10 🍊 The item shown as number {@code ord} in prompts ("[x] 2. ..."), if any. */
    public Optional<TaskItem> itemAt(int ord) {
        return items.stream().filter(item -> item.ord() == ord).findFirst();
    }
}
