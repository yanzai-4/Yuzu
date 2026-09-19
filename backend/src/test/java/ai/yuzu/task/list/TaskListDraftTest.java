package ai.yuzu.task.list;

import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.task.Actor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.10 🍊 Unit tests of the task-operation rules (no database). */
class TaskListDraftTest {

    private static final AgentId AGENT = AgentId.of("agent-3fa9");
    private static final String LIST_ID = "list-3fa9-00000000aa";
    private static final Instant T0 = Instant.parse("2026-09-19T18:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final Actor ALICE = Actor.human("user-0a0a", "Alice");

    /** v0.0.10 🍊 CHECK, STRIKE and NOTE change states and notes but never remove an item. */
    @Test
    void checkStrikeAndNoteKeepEveryItem() {
        TaskListDraft draft = draft(TaskListStatus.ACTIVE, item(1, TaskItemState.TODO), item(2, TaskItemState.DOING),
                item(3, TaskItemState.TODO));
        draft.applyAll(List.of(TaskOp.check(id(1), "found 12 sources"), TaskOp.strike(id(2), "scope changed"),
                TaskOp.note(id(3), "waiting for data")));

        assertThat(draft.problemMessages()).isEmpty();
        assertThat(draft.changed()).isTrue();
        assertThat(draft.changedItems()).hasSize(3);
        assertThat(draft.changedItems()).extracting(TaskItemRow::state)
                .containsExactly(TaskItemState.DONE, TaskItemState.STRUCK, TaskItemState.TODO);
        assertThat(draft.changedItems().get(0).note()).isEqualTo("found 12 sources");
        assertThat(draft.changedItems().get(1).struckReason()).isEqualTo("scope changed");
        assertThat(draft.changedItems().get(1).text()).isEqualTo("Item 2");
        assertThat(draft.changedItems().get(2).note()).isEqualTo("waiting for data");
    }

    /** v0.0.10 🍊 Re-checking, re-starting and re-striking are idempotent (nothing to write). */
    @Test
    void repeatedOperationsAreIdempotent() {
        TaskItemRow done = item(1, TaskItemState.DONE).withNote("found 12 sources", T0);
        TaskListDraft draft = draft(TaskListStatus.ACTIVE, done, item(2, TaskItemState.DOING),
                item(3, TaskItemState.STRUCK));
        draft.applyAll(List.of(TaskOp.check(id(1), null), TaskOp.check(id(1), "found 12 sources"),
                TaskOp.start(id(2)), TaskOp.strike(id(3), "another reason")));

        assertThat(draft.problemMessages()).isEmpty();
        assertThat(draft.changed()).isFalse();
        assertThat(draft.listRow().updatedAt()).isEqualTo(T0);
    }

    /** v0.0.10 🍊 A DONE item may still be struck through when a reason is given. */
    @Test
    void strikingADoneItemNeedsAReason() {
        TaskListDraft draft = draft(TaskListStatus.ACTIVE, item(1, TaskItemState.DONE));
        draft.applyAll(List.of(TaskOp.strike(id(1), "  ")));
        assertThat(draft.problemMessages()).singleElement().asString().contains("a reason is required");

        TaskListDraft retry = draft(TaskListStatus.ACTIVE, item(1, TaskItemState.DONE));
        retry.applyAll(List.of(TaskOp.strike(id(1), "the data was stale")));
        assertThat(retry.changedItems()).singleElement().satisfies(item -> {
            assertThat(item.state()).isEqualTo(TaskItemState.STRUCK);
            assertThat(item.struckReason()).isEqualTo("the data was stale");
        });
    }

    /** v0.0.10 🍊 Every invalid operation is reported in one pass; the first problem picks the exception type. */
    @Test
    void everyProblemIsReported() {
        TaskListDraft draft = draft(TaskListStatus.ACTIVE, item(1, TaskItemState.DONE), item(2, TaskItemState.STRUCK));
        draft.applyAll(Arrays.asList(TaskOp.check("item-3fa9-ffffffffff", null), TaskOp.start(id(1)),
                TaskOp.check(id(2), null), TaskOp.add(" "), null));

        assertThat(draft.problemMessages()).containsExactly(
                "Operation 1 (CHECK item-3fa9-ffffffffff) was rejected: item item-3fa9-ffffffffff is not on the "
                        + "current task list (archived lists cannot change).",
                "Operation 2 (START item-3fa9-0000000001) was rejected: item 1 \"Item 1\" is already done; ADD a new "
                        + "item to redo it.",
                "Operation 3 (CHECK item-3fa9-0000000002) was rejected: item 2 \"Item 2\" was struck and cannot be "
                        + "checked; ADD a new item if the work was needed after all.",
                "Operation 4 (ADD \"\") was rejected: the item text is empty.",
                "Operation 5 (empty) was rejected: the operation is missing.");
        assertThatThrownBy(draft::throwIfRejected)
                .isInstanceOf(NotFoundException.class)
                .hasMessageStartingWith("5 operations were rejected, so nothing was changed.")
                .satisfies(e -> {
                    assertThat((List<?>) ((YuzuException) e).details().get("problems")).hasSize(5);
                    assertThat(((YuzuException) e).agentId()).isEqualTo(AGENT.value());
                });
    }

    /** v0.0.10 🍊 Later operations see the effect of earlier ones in the same batch. */
    @Test
    void operationsSeeEarlierOnesInTheBatch() {
        TaskListDraft draft = draft(TaskListStatus.ACTIVE, item(1, TaskItemState.TODO));
        draft.applyAll(List.of(TaskOp.start(id(1)), TaskOp.strike(id(1), "blocked"), TaskOp.check(id(1), null)));
        assertThat(draft.problemMessages()).singleElement().asString().startsWith("Operation 3 (CHECK");
    }

    /** v0.0.10 🍊 ADD appends with the next ord, refuses duplicates of open items, and reopens an awaiting list. */
    @Test
    void addAppendsAndReopensAnAwaitingList() {
        TaskListDraft draft = draft(TaskListStatus.AWAITING_APPROVAL, item(1, TaskItemState.DONE),
                item(2, TaskItemState.STRUCK));
        draft.applyAll(List.of(TaskOp.add("  Also   check\nthe FAQ "), TaskOp.add("item 1")));
        assertThat(draft.problemMessages()).isEmpty();
        assertThat(draft.reopened()).isTrue();
        assertThat(draft.listRow().status()).isEqualTo(TaskListStatus.ACTIVE);
        assertThat(draft.listRow().completedAt()).isNull();
        assertThat(draft.addedItems()).extracting(TaskItemRow::ord).containsExactly(3, 4);
        assertThat(draft.addedItems().getFirst().text()).isEqualTo("Also check the FAQ");

        TaskListDraft duplicate = draft(TaskListStatus.ACTIVE, item(1, TaskItemState.TODO));
        duplicate.applyAll(List.of(TaskOp.add("ITEM 1")));
        assertThat(duplicate.problemMessages()).singleElement().asString().contains("already says that");
    }

    /** v0.0.10 🍊 EDIT_GOAL keeps the previous goal and its reason in the history. */
    @Test
    void editGoalKeepsTheOldGoal() {
        TaskListDraft draft = draft(TaskListStatus.ACTIVE, item(1, TaskItemState.TODO));
        draft.applyAll(List.of(TaskOp.editGoal("Ship the pricing page", "Alice asked for pricing"),
                TaskOp.editGoal("Ship the pricing page", "again")));
        assertThat(draft.problemMessages()).isEmpty();
        TaskListRow row = draft.listRow();
        assertThat(row.goal()).isEqualTo("Ship the pricing page");
        assertThat(row.goalHistory()).containsExactly(new GoalChange("Ship the page", "Alice asked for pricing", T1));

        TaskListDraft noReason = draft(TaskListStatus.ACTIVE);
        noReason.applyAll(List.of(TaskOp.editGoal("Other", null)));
        assertThat(noReason.problemMessages()).singleElement().asString().contains("a reason is required");
    }

    /** v0.0.10 🍊 Notes are appended with "; " and a note already present is not repeated. */
    @Test
    void notesAppendWithoutRepeating() {
        TaskListDraft draft = draft(TaskListStatus.ACTIVE, item(1, TaskItemState.TODO));
        draft.applyAll(List.of(TaskOp.note(id(1), "asked Lime"), TaskOp.note(id(1), "Lime answered"),
                TaskOp.note(id(1), "asked Lime")));
        assertThat(draft.changedItems()).singleElement().extracting(TaskItemRow::note)
                .isEqualTo("asked Lime; Lime answered");
    }

    /** v0.0.10 🍊 Initial items of a new list follow the ADD rules and are reported as "Item N". */
    @Test
    void newListItemsFollowTheAddRules() {
        TaskListRow fresh = TaskListRow.fresh(AGENT, "Goal", ALICE, null, T1);
        TaskListDraft draft = TaskListDraft.forNewList(fresh, List.of("Collect", "", "Compare"), T1);
        assertThat(draft.problemMessages()).containsExactly(
                "Item 2 (ADD \"\") was rejected: the item text is empty.");
        assertThatThrownBy(draft::throwIfRejected).isInstanceOf(BadRequestException.class)
                .hasMessage("Item 2 (ADD \"\") was rejected: the item text is empty. Nothing was changed.");

        TaskListDraft clean = TaskListDraft.forNewList(fresh, List.of("Collect", "Compare"), T1);
        assertThat(clean.addedItems()).extracting(TaskItemRow::ord).containsExactly(1, 2);
    }

    /** v0.0.10 🍊 Archived lists can never be drafted. */
    @Test
    void archivedListsCannotBeDrafted() {
        assertThatThrownBy(() -> draft(TaskListStatus.ARCHIVED)).isInstanceOf(IllegalStateException.class);
    }

    /** v0.0.10 🍊 A draft of the list in the given status with the given items (version 4, created at T0). */
    private static TaskListDraft draft(TaskListStatus status, TaskItemRow... items) {
        TaskListRow list = new TaskListRow(AGENT, LIST_ID, "Ship the page", List.of(), status, ALICE, null, null, null,
                4, T0, T0, status == TaskListStatus.AWAITING_APPROVAL ? T0 : null, null, null);
        return TaskListDraft.of(list, List.of(items), T1);
    }

    /** v0.0.10 🍊 A persisted item "Item N" in the given state. */
    private static TaskItemRow item(int ord, TaskItemState state) {
        return new TaskItemRow(AGENT, id(ord), LIST_ID, ord, "Item " + ord, state, null,
                state == TaskItemState.STRUCK ? "first reason" : null, T0, T0);
    }

    /** v0.0.10 🍊 The id of item N. */
    private static String id(int ord) {
        return String.format("item-3fa9-%010d", ord);
    }
}
