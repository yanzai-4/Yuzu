package ai.yuzu.task.list;

import ai.yuzu.task.Actor;

import java.util.List;

/** v0.0.20 🍊 Writes the one-sentence outcome ("how it went") stored on a task list when it is archived. */
final class OutcomeWriter {

    /** v0.0.20 🍊 Static helpers only. */
    private OutcomeWriter() {
    }

    /** v0.0.20 🍊 "Completed with changes: 3 of 4 items done, 1 struck. Approved by Alice (human)." and similar. */
    static String summarize(List<TaskItemRow> items, Actor approver) {
        long done = items.stream().filter(item -> item.state() == TaskItemState.DONE).count();
        long struck = items.stream().filter(item -> item.state() == TaskItemState.STRUCK).count();
        String how;
        if (struck == 0) {
            how = "Completed: " + count(done) + " done.";
        } else if (done == 0) {
            how = "Dropped: " + count(struck) + " struck.";
        } else {
            how = "Completed with changes: " + done + " of " + count(items.size()) + " done, " + struck + " struck.";
        }
        return how + " Approved by " + approver.describe() + ".";
    }

    /** v0.0.20 🍊 "1 item" or "N items". */
    private static String count(long n) {
        return n + (n == 1 ? " item" : " items");
    }
}
