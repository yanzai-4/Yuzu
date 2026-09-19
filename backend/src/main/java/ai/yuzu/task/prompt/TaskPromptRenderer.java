package ai.yuzu.task.prompt;

import ai.yuzu.task.Actor;
import ai.yuzu.task.TaskText;
import ai.yuzu.task.list.PreviousGoal;
import ai.yuzu.task.list.TaskItem;
import ai.yuzu.task.list.TaskItemState;
import ai.yuzu.task.list.TaskList;
import ai.yuzu.task.list.TaskListStatus;
import ai.yuzu.task.list.TaskListView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** v0.0.10 🍊 Byte-stable English renderings of task lists for LLM prompts (absolute times only). */
public final class TaskPromptRenderer {

    private static final int SHOWN_PREVIOUS_GOALS = 3;

    /** v0.0.10 🍊 Static helpers only. */
    private TaskPromptRenderer() {
    }

    /** v0.0.10 🍊 The agent's current list ("Goal: ...", "[x] 1. ..."), or a fixed sentence when there is none. */
    public static String renderCurrent(TaskListView view) {
        if (view == null || view.current() == null) {
            return "No current task list.";
        }
        return renderList(view.current());
    }

    /** v0.0.10 🍊 One list: goal, earlier goals, publisher, status, ticket, start time, progress, then every item. */
    public static String renderList(TaskList list) {
        List<String> lines = new ArrayList<>();
        lines.add("Goal: " + TaskText.oneLine(list.goal()));
        List<PreviousGoal> previous = list.previousGoals();
        int from = Math.max(0, previous.size() - SHOWN_PREVIOUS_GOALS);
        for (PreviousGoal goal : previous.subList(from, previous.size())) {
            lines.add("Earlier goal: " + TaskText.oneLine(goal.goal()) + " (replaced " + goal.time() + "; reason: "
                    + TaskText.oneLine(goal.reason()) + ")");
        }
        lines.add("Publisher: " + publisher(list));
        lines.add("Status: " + status(list));
        if (list.ticketId() != null) {
            lines.add("Ticket: " + list.ticketId());
        }
        lines.add("Started: " + list.time());
        lines.add("Progress: " + progress(list.items()));
        for (TaskItem item : ordered(list.items())) {
            lines.add(itemLine(item));
        }
        return String.join("\n", lines);
    }

    /** v0.0.10 🍊 The latest archived lists in the given order (most recent first) with their outcome and items. */
    public static String renderHistory(List<TaskList> archived) {
        if (archived == null || archived.isEmpty()) {
            return "No archived task lists yet.";
        }
        List<String> lines = new ArrayList<>();
        lines.add("Recent archived task lists (most recent first):");
        for (int i = 0; i < archived.size(); i++) {
            TaskList list = archived.get(i);
            lines.add((i + 1) + ". Goal: " + TaskText.oneLine(list.goal()));
            lines.add("   Publisher: " + publisher(list));
            lines.add("   Started: " + list.time());
            if (list.archivedTime() != null) {
                lines.add("   Archived: " + list.archivedTime());
            }
            lines.add("   Outcome: " + (list.outcome() == null ? "(not recorded)" : TaskText.oneLine(list.outcome())));
            for (TaskItem item : ordered(list.items())) {
                lines.add("   " + itemLine(item));
            }
        }
        return String.join("\n", lines);
    }

    /** v0.0.10 🍊 "[ ] 4. text", "[>] 3. text (in progress)", "[x] 1. text", "[~] 2. text (struck: reason)". */
    static String itemLine(TaskItem item) {
        List<String> annotations = new ArrayList<>();
        if (item.state() == TaskItemState.DOING) {
            annotations.add("in progress");
        } else if (item.state() == TaskItemState.STRUCK) {
            annotations.add("struck: " + TaskText.oneLine(item.struckReason()));
        }
        String note = TaskText.oneLine(item.note());
        if (!note.isEmpty()) {
            annotations.add("note: " + note);
        }
        String suffix = annotations.isEmpty() ? "" : " (" + String.join("; ", annotations) + ")";
        return marker(item.state()) + " " + item.ord() + ". " + TaskText.oneLine(item.text()) + suffix;
    }

    /** v0.0.10 🍊 Checkbox marker of an item state. */
    private static String marker(TaskItemState state) {
        return switch (state) {
            case TODO -> "[ ]";
            case DOING -> "[>]";
            case DONE -> "[x]";
            case STRUCK -> "[~]";
        };
    }

    /** v0.0.10 🍊 "Alice (human)" or "Yuzu (agent)"; the kind falls back to the id shape. */
    private static String publisher(TaskList list) {
        Actor.Kind kind = list.publisherKind() != null ? list.publisherKind()
                : list.publisherId() != null && list.publisherId().startsWith("agent-") ? Actor.Kind.AGENT
                : Actor.Kind.HUMAN;
        return TaskText.oneLine(list.publisherName()) + (kind == Actor.Kind.AGENT ? " (agent)" : " (human)");
    }

    /** v0.0.10 🍊 Status sentence: in progress, awaiting approval (and by whom), or archived (and when). */
    private static String status(TaskList list) {
        TaskListStatus status = list.status();
        return switch (status) {
            case ACTIVE -> "in progress";
            case AWAITING_APPROVAL -> "every item is finished; awaiting approval by " + publisher(list)
                    + " or any human";
            case ARCHIVED -> "archived" + (list.archivedTime() == null ? "" : " " + list.archivedTime());
        };
    }

    /** v0.0.10 🍊 "2 of 4 finished (1 done, 1 struck, 1 in progress, 1 to do)" or "no items yet". */
    private static String progress(List<TaskItem> items) {
        if (items.isEmpty()) {
            return "no items yet";
        }
        long done = count(items, TaskItemState.DONE);
        long struck = count(items, TaskItemState.STRUCK);
        return (done + struck) + " of " + items.size() + " finished (" + done + " done, " + struck + " struck, "
                + count(items, TaskItemState.DOING) + " in progress, " + count(items, TaskItemState.TODO) + " to do)";
    }

    /** v0.0.10 🍊 Number of items in a state. */
    private static long count(List<TaskItem> items, TaskItemState state) {
        return items.stream().filter(item -> item.state() == state).count();
    }

    /** v0.0.10 🍊 Items in display order (by ord), whatever order they arrived in. */
    private static List<TaskItem> ordered(List<TaskItem> items) {
        return items.stream().sorted(Comparator.comparingInt(TaskItem::ord)).toList();
    }
}
