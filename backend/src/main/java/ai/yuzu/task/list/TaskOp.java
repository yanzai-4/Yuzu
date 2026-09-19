package ai.yuzu.task.list;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** v0.0.20 🍊 One change the planning module proposes for a current task list; JSON uses an "op" discriminator. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "op")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TaskOp.Add.class, name = "ADD"),
        @JsonSubTypes.Type(value = TaskOp.Start.class, name = "START"),
        @JsonSubTypes.Type(value = TaskOp.Check.class, name = "CHECK"),
        @JsonSubTypes.Type(value = TaskOp.Strike.class, name = "STRIKE"),
        @JsonSubTypes.Type(value = TaskOp.EditGoal.class, name = "EDIT_GOAL"),
        @JsonSubTypes.Type(value = TaskOp.Note.class, name = "NOTE")
})
public sealed interface TaskOp {

    /** v0.0.20 🍊 Operation kinds. */
    enum Kind { ADD, START, CHECK, STRIKE, EDIT_GOAL, NOTE }

    /** v0.0.20 🍊 The kind of this operation. */
    Kind kind();

    /** v0.0.20 🍊 Short label for error messages, such as {@code CHECK item-3fa9-0123456789}. */
    String summary();

    /** v0.0.20 🍊 Appends a new TODO item (reopens a list that was awaiting approval). */
    static TaskOp add(String text) {
        return new Add(text);
    }

    /** v0.0.20 🍊 Marks an item as in progress. */
    static TaskOp start(String itemId) {
        return new Start(itemId);
    }

    /** v0.0.20 🍊 Checks an item off (DONE), optionally with a note; re-checking is idempotent. */
    static TaskOp check(String itemId, String note) {
        return new Check(itemId, note);
    }

    /** v0.0.20 🍊 Strikes an item through with a reason (plans changed); the item stays visible. */
    static TaskOp strike(String itemId, String reason) {
        return new Strike(itemId, reason);
    }

    /** v0.0.20 🍊 Replaces the goal; the old goal and the reason are kept in the goal history. */
    static TaskOp editGoal(String goal, String reason) {
        return new EditGoal(goal, reason);
    }

    /** v0.0.20 🍊 Appends a progress note to an item. */
    static TaskOp note(String itemId, String note) {
        return new Note(itemId, note);
    }

    /** v0.0.20 🍊 ADD: a new TODO item. */
    record Add(String text) implements TaskOp {
        /** v0.0.20 🍊 Always ADD. */
        @Override
        public Kind kind() {
            return Kind.ADD;
        }

        /** v0.0.20 🍊 ADD with the start of the text. */
        @Override
        public String summary() {
            return "ADD \"" + shorten(text) + "\"";
        }
    }

    /** v0.0.20 🍊 START: the item is being worked on (DOING). */
    record Start(String itemId) implements TaskOp {
        /** v0.0.20 🍊 Always START. */
        @Override
        public Kind kind() {
            return Kind.START;
        }

        /** v0.0.20 🍊 START with the item id. */
        @Override
        public String summary() {
            return "START " + itemId;
        }
    }

    /** v0.0.20 🍊 CHECK: the item is done (optional note). */
    record Check(String itemId, String note) implements TaskOp {
        /** v0.0.20 🍊 Always CHECK. */
        @Override
        public Kind kind() {
            return Kind.CHECK;
        }

        /** v0.0.20 🍊 CHECK with the item id. */
        @Override
        public String summary() {
            return "CHECK " + itemId;
        }
    }

    /** v0.0.20 🍊 STRIKE: the item is no longer part of the plan (reason required). */
    record Strike(String itemId, String reason) implements TaskOp {
        /** v0.0.20 🍊 Always STRIKE. */
        @Override
        public Kind kind() {
            return Kind.STRIKE;
        }

        /** v0.0.20 🍊 STRIKE with the item id. */
        @Override
        public String summary() {
            return "STRIKE " + itemId;
        }
    }

    /** v0.0.20 🍊 EDIT_GOAL: a new goal (reason required; the old goal is kept). */
    record EditGoal(String goal, String reason) implements TaskOp {
        /** v0.0.20 🍊 Always EDIT_GOAL. */
        @Override
        public Kind kind() {
            return Kind.EDIT_GOAL;
        }

        /** v0.0.20 🍊 EDIT_GOAL with the start of the new goal. */
        @Override
        public String summary() {
            return "EDIT_GOAL \"" + shorten(goal) + "\"";
        }
    }

    /** v0.0.20 🍊 NOTE: a progress note appended to an item. */
    record Note(String itemId, String note) implements TaskOp {
        /** v0.0.20 🍊 Always NOTE. */
        @Override
        public Kind kind() {
            return Kind.NOTE;
        }

        /** v0.0.20 🍊 NOTE with the item id. */
        @Override
        public String summary() {
            return "NOTE " + itemId;
        }
    }

    /** v0.0.20 🍊 One-line text cut to 40 characters with an ellipsis (for operation summaries). */
    private static String shorten(String text) {
        String line = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        return line.length() <= 40 ? line : line.substring(0, 39) + "…";
    }
}
