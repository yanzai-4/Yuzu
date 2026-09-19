package ai.yuzu.task.list;

import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.ConflictException;
import ai.yuzu.common.error.ErrorCode;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.task.TaskText;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** v0.0.20 🍊 Working copy of an open task list that applies TaskOps under the code-level rules (all-or-nothing). */
final class TaskListDraft {

    // Operations run in order against this evolving copy. An invalid operation is recorded as a problem and
    // skipped, so a single pass reports every problem (useful as retry feedback for the planning LLM). Callers
    // persist the result only when there are no problems, which makes every batch all-or-nothing.

    static final int MAX_ITEMS = 50;
    static final int MAX_ITEM_TEXT = 500;
    static final int MAX_NOTE_PART = 500;
    static final int MAX_NOTE = 2_000;
    static final int MAX_REASON = 500;
    static final int MAX_GOAL = 1_000;
    static final int MAX_GOAL_EDITS = 20;
    private static final String NOTE_SEPARATOR = "; ";

    private final TaskListRow base;
    private final Instant now;
    private final String label;
    private final List<TaskItemRow> items;
    private final Map<String, Integer> indexById = new HashMap<>();
    private final Set<String> changedIds = new LinkedHashSet<>();
    private final List<GoalChange> goalHistory;
    private final List<Problem> problems = new ArrayList<>();
    private String goal;
    private TaskListStatus status;
    private Instant completedAt;
    private int nextOrd;
    private boolean listChanged;
    private boolean reopened;

    /** v0.0.20 🍊 A problem found while applying one operation (1-based position, error code, English reason). */
    record Problem(int position, String operation, ErrorCode code, String reason) {

        /** v0.0.20 🍊 Full sentence such as {@code Operation 2 (CHECK item-...) was rejected: ...}. */
        String describe(String label) {
            return label + " " + position + " (" + operation + ") was rejected: " + reason + ".";
        }
    }

    /** v0.0.20 🍊 Copies the list and its items (sorted by ord) into a mutable working state. */
    private TaskListDraft(TaskListRow base, List<TaskItemRow> existing, Instant now, String label) {
        this.base = base;
        this.now = now;
        this.label = label;
        this.items = new ArrayList<>(existing.stream().sorted(Comparator.comparingInt(TaskItemRow::ord)).toList());
        for (int i = 0; i < items.size(); i++) {
            indexById.put(items.get(i).id(), i);
        }
        this.goalHistory = new ArrayList<>(base.goalHistory());
        this.goal = base.goal();
        this.status = base.status();
        this.completedAt = base.completedAt();
        this.nextOrd = items.stream().mapToInt(TaskItemRow::ord).max().orElse(0) + 1;
    }

    /** v0.0.20 🍊 Draft over an existing open list (archived lists can never be drafted). */
    static TaskListDraft of(TaskListRow list, List<TaskItemRow> items, Instant now) {
        if (!list.status().isOpen()) {
            throw new IllegalStateException("Archived task list " + list.id() + " cannot change.");
        }
        return new TaskListDraft(list, items, now, "Operation");
    }

    /** v0.0.20 🍊 Draft of a brand-new list whose initial items go through the same rules as ADD. */
    static TaskListDraft forNewList(TaskListRow fresh, List<String> itemTexts, Instant now) {
        TaskListDraft draft = new TaskListDraft(fresh, List.of(), now, "Item");
        List<String> texts = itemTexts == null ? List.of() : itemTexts;
        for (int i = 0; i < texts.size(); i++) {
            draft.apply(i + 1, new TaskOp.Add(texts.get(i)));
        }
        return draft;
    }

    /** v0.0.20 🍊 Applies the operations in order, recording (and skipping) every invalid one. */
    void applyAll(List<TaskOp> ops) {
        for (int i = 0; i < ops.size(); i++) {
            apply(i + 1, ops.get(i));
        }
    }

    /** v0.0.20 🍊 English description of every rejected operation, in order. */
    List<String> problemMessages() {
        return problems.stream().map(p -> p.describe(label)).toList();
    }

    /** v0.0.20 🍊 Throws NOT_FOUND, CONFLICT or BAD_REQUEST (first problem) listing every problem; no-op if clean. */
    void throwIfRejected() {
        if (problems.isEmpty()) {
            return;
        }
        List<String> all = problemMessages();
        String message = all.size() == 1 ? all.getFirst() + " Nothing was changed."
                : all.size() + " operations were rejected, so nothing was changed. " + String.join(" ", all);
        YuzuException error = switch (problems.getFirst().code()) {
            case NOT_FOUND -> new NotFoundException(message);
            case CONFLICT -> new ConflictException(message);
            default -> new BadRequestException(message);
        };
        error.with("problems", all).with("listId", base.id()).forAgent(base.agentId().value());
        throw error;
    }

    /** v0.0.20 🍊 True when at least one operation changed the list or its items. */
    boolean changed() {
        return listChanged || !changedIds.isEmpty() || items.stream().anyMatch(TaskItemRow::isNew);
    }

    /** v0.0.20 🍊 True when the batch moved an AWAITING_APPROVAL list back to ACTIVE (new work arrived). */
    boolean reopened() {
        return reopened;
    }

    /** v0.0.20 🍊 The list row after the batch (same version; the store bumps it). */
    TaskListRow listRow() {
        return base.edited(goal, goalHistory, status, completedAt, changed() ? now : base.updatedAt());
    }

    /** v0.0.20 🍊 Existing items whose state or note changed. */
    List<TaskItemRow> changedItems() {
        return items.stream().filter(item -> !item.isNew() && changedIds.contains(item.id())).toList();
    }

    /** v0.0.20 🍊 Items added by the batch, in ord order (ids are generated on insert). */
    List<TaskItemRow> addedItems() {
        return items.stream().filter(TaskItemRow::isNew).toList();
    }

    /** v0.0.20 🍊 Dispatches one operation to its rule. */
    private void apply(int position, TaskOp op) {
        switch (op) {
            case null -> reject(position, "empty", ErrorCode.BAD_REQUEST, "the operation is missing");
            case TaskOp.Add add -> add(position, add);
            case TaskOp.Start start -> start(position, start);
            case TaskOp.Check check -> check(position, check);
            case TaskOp.Strike strike -> strike(position, strike);
            case TaskOp.EditGoal edit -> editGoal(position, edit);
            case TaskOp.Note note -> note(position, note);
        }
    }

    /** v0.0.20 🍊 ADD: a new TODO item; duplicates of unfinished items are refused; reopens an awaiting list. */
    private void add(int position, TaskOp.Add op) {
        String text = TaskText.oneLine(op.text());
        if (text.isEmpty()) {
            reject(position, op, ErrorCode.BAD_REQUEST, "the item text is empty");
            return;
        }
        if (text.length() > MAX_ITEM_TEXT) {
            reject(position, op, ErrorCode.BAD_REQUEST,
                    "the item text is longer than " + MAX_ITEM_TEXT + " characters");
            return;
        }
        if (items.size() >= MAX_ITEMS) {
            reject(position, op, ErrorCode.CONFLICT, "a task list holds at most " + MAX_ITEMS + " items");
            return;
        }
        Optional<TaskItemRow> duplicate = items.stream()
                .filter(item -> !item.state().isFinished() && item.text().equalsIgnoreCase(text)).findFirst();
        if (duplicate.isPresent()) {
            reject(position, op, ErrorCode.CONFLICT,
                    describe(duplicate.get()) + " already says that and is not finished");
            return;
        }
        items.add(TaskItemRow.fresh(base.agentId(), nextOrd++, text, now));
        reopen();
    }

    /** v0.0.20 🍊 START: TODO becomes DOING; DOING stays; DONE and STRUCK items cannot restart. */
    private void start(int position, TaskOp.Start op) {
        Integer index = locate(position, op, op.itemId());
        if (index == null) {
            return;
        }
        TaskItemRow item = items.get(index);
        switch (item.state()) {
            case TODO -> replace(index, item.withState(TaskItemState.DOING, now));
            case DOING -> {
                // Already in progress: idempotent.
            }
            case DONE -> reject(position, op, ErrorCode.CONFLICT,
                    describe(item) + " is already done; ADD a new item to redo it");
            case STRUCK -> reject(position, op, ErrorCode.CONFLICT,
                    describe(item) + " was struck; ADD a new item if the work is needed again");
        }
    }

    /** v0.0.20 🍊 CHECK: TODO or DOING becomes DONE; re-checking a DONE item is idempotent; STRUCK items stay struck. */
    private void check(int position, TaskOp.Check op) {
        String note = TaskText.oneLine(op.note());
        if (note.length() > MAX_NOTE_PART) {
            reject(position, op, ErrorCode.BAD_REQUEST, "the note is longer than " + MAX_NOTE_PART + " characters");
            return;
        }
        Integer index = locate(position, op, op.itemId());
        if (index == null) {
            return;
        }
        TaskItemRow item = items.get(index);
        if (item.state() == TaskItemState.STRUCK) {
            reject(position, op, ErrorCode.CONFLICT, describe(item)
                    + " was struck and cannot be checked; ADD a new item if the work was needed after all");
            return;
        }
        TaskItemRow done = item.state() == TaskItemState.DONE ? item : item.withState(TaskItemState.DONE, now);
        TaskItemRow noted = appendNote(position, op, done, note);
        if (noted != null) {
            replace(index, noted);
        }
    }

    /** v0.0.20 🍊 STRIKE: any item can be struck with a reason (DONE included); re-striking keeps the first reason. */
    private void strike(int position, TaskOp.Strike op) {
        String reason = TaskText.oneLine(op.reason());
        if (reason.isEmpty()) {
            reject(position, op, ErrorCode.BAD_REQUEST, "a reason is required to strike an item");
            return;
        }
        if (reason.length() > MAX_REASON) {
            reject(position, op, ErrorCode.BAD_REQUEST, "the reason is longer than " + MAX_REASON + " characters");
            return;
        }
        Integer index = locate(position, op, op.itemId());
        if (index == null) {
            return;
        }
        TaskItemRow item = items.get(index);
        if (item.state() != TaskItemState.STRUCK) {
            replace(index, item.struck(reason, now));
        }
    }

    /** v0.0.20 🍊 EDIT_GOAL: replaces the goal and keeps the old one with the reason; reopens an awaiting list. */
    private void editGoal(int position, TaskOp.EditGoal op) {
        String next = TaskText.oneLine(op.goal());
        String reason = TaskText.oneLine(op.reason());
        if (next.isEmpty()) {
            reject(position, op, ErrorCode.BAD_REQUEST, "the new goal is empty");
        } else if (next.length() > MAX_GOAL) {
            reject(position, op, ErrorCode.BAD_REQUEST, "the goal is longer than " + MAX_GOAL + " characters");
        } else if (reason.isEmpty()) {
            reject(position, op, ErrorCode.BAD_REQUEST, "a reason is required to change the goal");
        } else if (reason.length() > MAX_REASON) {
            reject(position, op, ErrorCode.BAD_REQUEST, "the reason is longer than " + MAX_REASON + " characters");
        } else if (!next.equals(goal)) {
            if (goalHistory.size() >= MAX_GOAL_EDITS) {
                reject(position, op, ErrorCode.CONFLICT, "the goal already changed " + MAX_GOAL_EDITS
                        + " times; finish this list and start a new one");
                return;
            }
            goalHistory.add(new GoalChange(goal, reason, now));
            goal = next;
            reopen();
        }
    }

    /** v0.0.20 🍊 NOTE: appends a progress note to any item (a note already present is not repeated). */
    private void note(int position, TaskOp.Note op) {
        String note = TaskText.oneLine(op.note());
        if (note.isEmpty()) {
            reject(position, op, ErrorCode.BAD_REQUEST, "the note is empty");
            return;
        }
        if (note.length() > MAX_NOTE_PART) {
            reject(position, op, ErrorCode.BAD_REQUEST, "the note is longer than " + MAX_NOTE_PART + " characters");
            return;
        }
        Integer index = locate(position, op, op.itemId());
        if (index == null) {
            return;
        }
        TaskItemRow noted = appendNote(position, op, items.get(index), note);
        if (noted != null) {
            replace(index, noted);
        }
    }

    /** v0.0.20 🍊 The item with a note appended; the same item when there is nothing new; null (rejected) when full. */
    private TaskItemRow appendNote(int position, TaskOp op, TaskItemRow item, String note) {
        String current = item.note();
        if (note.isEmpty() || (current != null && current.contains(note))) {
            return item;
        }
        String merged = current == null || current.isEmpty() ? note : current + NOTE_SEPARATOR + note;
        if (merged.length() > MAX_NOTE) {
            reject(position, op, ErrorCode.BAD_REQUEST, "the notes of " + describe(item) + " would exceed "
                    + MAX_NOTE + " characters");
            return null;
        }
        return item.withNote(merged, now);
    }

    /** v0.0.20 🍊 Index of the referenced item, or null after recording why it cannot be used. */
    private Integer locate(int position, TaskOp op, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            reject(position, op, ErrorCode.BAD_REQUEST, "the item id is missing");
            return null;
        }
        Integer index = indexById.get(itemId.strip());
        if (index == null) {
            reject(position, op, ErrorCode.NOT_FOUND, "item " + itemId.strip()
                    + " is not on the current task list (archived lists cannot change)");
        }
        return index;
    }

    /** v0.0.20 🍊 Stores a changed item; the same instance means nothing changed. */
    private void replace(int index, TaskItemRow next) {
        TaskItemRow previous = items.get(index);
        if (next == previous) {
            return;
        }
        items.set(index, next);
        if (!previous.isNew()) {
            changedIds.add(previous.id());
        }
    }

    /** v0.0.20 🍊 Marks the list changed; new work on an AWAITING_APPROVAL list sends it back to ACTIVE. */
    private void reopen() {
        listChanged = true;
        if (status == TaskListStatus.AWAITING_APPROVAL) {
            status = TaskListStatus.ACTIVE;
            completedAt = null;
            reopened = true;
        }
    }

    /** v0.0.20 🍊 Records a rejected operation. */
    private void reject(int position, TaskOp op, ErrorCode code, String reason) {
        reject(position, op.summary(), code, reason);
    }

    /** v0.0.20 🍊 Records a rejected operation with an explicit label. */
    private void reject(int position, String operation, ErrorCode code, String reason) {
        problems.add(new Problem(position, operation, code, reason));
    }

    /** v0.0.20 🍊 "item 3 \"Draft the summary\"" for messages. */
    private static String describe(TaskItemRow item) {
        String text = item.text().length() <= 60 ? item.text() : item.text().substring(0, 59) + "…";
        return "item " + item.ord() + " \"" + text + "\"";
    }
}
