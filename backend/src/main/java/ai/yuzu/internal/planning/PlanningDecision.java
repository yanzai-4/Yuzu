package ai.yuzu.internal.planning;

import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.Nullable;

import java.util.List;

/** v0.0.21 🍊 Output of the planning module: how (if at all) this input changes my task list. */
public record PlanningDecision(
        @Desc("Brief reasoning: does this input change my work plan?") String reasoning,
        @Desc("NONE: nothing to change; CREATE: start a new task list (only when I have no current list); UPDATE: change my current list") Mode mode,
        @Nullable @Desc("CREATE only: the goal of the new list in one sentence; null otherwise") String goal,
        @Desc("CREATE only: the steps of the new list in order (1 to 12 short items); empty otherwise") List<String> items,
        @Nullable @Desc("CREATE only: exact name of whoever gave me this work (a human, or the coworker who assigned it); they approve the list when it is done") String publisher,
        @Nullable @Desc("CREATE only: the id of the ticket this work belongs to, when there is one; null otherwise") String ticketId,
        @Desc("UPDATE only: the changes in order; empty otherwise") List<Op> ops,
        @Desc("true only when every item of my list is done or struck and I should ask my publisher to approve it") boolean requestApproval) {

    /** v0.0.21 🍊 What to do with the task list. */
    public enum Mode { NONE, CREATE, UPDATE }

    /** v0.0.21 🍊 One change of the current list; items are referenced by their number. */
    public record Op(
            @Desc("ADD | START | CHECK | STRIKE | EDIT_GOAL | NOTE") Kind op,
            @Nullable @Desc("The item number as shown in my list, for START, CHECK, STRIKE and NOTE; null for ADD and EDIT_GOAL") Integer item,
            @Nullable @Desc("ADD: the new item; CHECK and NOTE: a short note (may be null for CHECK); STRIKE and EDIT_GOAL: the reason") String text,
            @Nullable @Desc("EDIT_GOAL only: the new goal; null otherwise") String goal) {

        /** v0.0.21 🍊 Operation kinds. */
        public enum Kind { ADD, START, CHECK, STRIKE, EDIT_GOAL, NOTE }
    }
}
