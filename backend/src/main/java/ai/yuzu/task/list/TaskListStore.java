package ai.yuzu.task.list;

import ai.yuzu.common.error.ConflictException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** v0.0.20 🍊 Persists task-list aggregates (list + items) and loads per-agent views inside the caller's transaction. */
@Component
class TaskListStore {

    static final int RECENT_ARCHIVED = 3;

    private final TaskListRepository lists;
    private final TaskItemRepository items;
    private final NaturalTime time;

    /** v0.0.20 🍊 Injects the repositories and the natural-time renderer used by the views. */
    TaskListStore(TaskListRepository lists, TaskItemRepository items, NaturalTime time) {
        this.lists = lists;
        this.items = items;
        this.time = time;
    }

    /** v0.0.20 🍊 The agent's open list, if any. */
    Optional<TaskListRow> findOpen(AgentId agentId) {
        return lists.findOpen(agentId);
    }

    /** v0.0.20 🍊 One of the agent's lists by id. */
    Optional<TaskListRow> findById(AgentId agentId, String listId) {
        return lists.findById(agentId, listId);
    }

    /** v0.0.20 🍊 The items of one list in display order. */
    List<TaskItemRow> itemsOf(AgentId agentId, String listId) {
        return items.findByList(agentId, listId);
    }

    /** v0.0.20 🍊 Inserts a new list with its initial items; CONFLICT when the one-open-list key is already taken. */
    String insert(TaskListDraft draft) {
        TaskListRow row = draft.listRow();
        String listId;
        try {
            listId = lists.insert(row);
        } catch (DuplicateKeyException e) {
            if (!String.valueOf(e.getMessage()).contains("uk_open_list")) {
                throw e;
            }
            throw new ConflictException("Agent " + row.agentId() + " already has a current task list; it must be "
                    + "approved and archived before another one starts.").forAgent(row.agentId().value());
        }
        for (TaskItemRow item : draft.addedItems()) {
            items.insert(item.inList(listId));
        }
        return listId;
    }

    /** v0.0.20 🍊 Writes a changed draft: the version-checked list row first, then changed and added items. */
    void save(TaskListDraft draft) {
        TaskListRow row = draft.listRow();
        update(row, row.version());
        for (TaskItemRow item : draft.changedItems()) {
            items.update(item);
        }
        for (TaskItemRow item : draft.addedItems()) {
            items.insert(item.inList(row.id()));
        }
    }

    /** v0.0.20 🍊 Version-checked list update; CONFLICT when someone else changed the list meanwhile. */
    void update(TaskListRow next, int expectedVersion) {
        if (!lists.update(next, expectedVersion)) {
            throw new ConflictException("Task list " + next.id() + " was changed concurrently; reload it and retry.")
                    .with("listId", next.id()).forAgent(next.agentId().value());
        }
    }

    /** v0.0.20 🍊 The agent's view: the open list with its items plus the latest archived lists with theirs. */
    TaskListView loadView(AgentId agentId) {
        TaskList current = lists.findOpen(agentId)
                .map(row -> row.toView(items.findByList(agentId, row.id()), time))
                .orElse(null);
        return new TaskListView(agentId.value(), current, loadArchived(agentId, RECENT_ARCHIVED));
    }

    /** v0.0.20 🍊 The latest archived lists with their items, most recent first. */
    List<TaskList> loadArchived(AgentId agentId, int limit) {
        List<TaskListRow> archived = lists.findArchived(agentId, limit);
        Map<String, List<TaskItemRow>> byList = items.findByLists(agentId,
                archived.stream().map(TaskListRow::id).toList());
        return archived.stream().map(row -> row.toView(byList.getOrDefault(row.id(), List.of()), time)).toList();
    }
}
