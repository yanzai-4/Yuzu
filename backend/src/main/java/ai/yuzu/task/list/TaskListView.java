package ai.yuzu.task.list;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** v0.0.10 🍊 An agent's current task list (null when none) and latest archived lists (contract TaskListView). */
public record TaskListView(String agentId, @JsonInclude(JsonInclude.Include.ALWAYS) TaskList current,
                           List<TaskList> recentArchived) {

    /** v0.0.10 🍊 Defensive, immutable copy of the archived lists. */
    public TaskListView {
        recentArchived = recentArchived == null ? List.of() : List.copyOf(recentArchived);
    }
}
