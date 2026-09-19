package ai.yuzu.task.prompt;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.module.TaskStateProvider;
import ai.yuzu.task.list.TaskListService;
import org.springframework.stereotype.Component;

/** v0.0.21 🍊 Supplies the task-list text of every module prompt from the task package (replaces the NONE fallback). */
@Component
public class TaskStateAdapter implements TaskStateProvider {

    private final TaskListService lists;

    /** v0.0.21 🍊 Injects the task-list service. */
    public TaskStateAdapter(TaskListService lists) {
        this.lists = lists;
    }

    /** v0.0.21 🍊 The current list with numbered items and progress. */
    @Override
    public String current(AgentId agentId) {
        return TaskPromptRenderer.renderCurrent(lists.current(agentId));
    }

    /** v0.0.21 🍊 The last 3 archived lists and how they went. */
    @Override
    public String history(AgentId agentId) {
        return TaskPromptRenderer.renderHistory(lists.recentArchived(agentId, TaskListService.RECENT_ARCHIVED));
    }
}
