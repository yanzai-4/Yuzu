package ai.yuzu.tool.spi;

import java.time.Instant;

/**
 * v0.0.18 🍊 Outcome of one tool call.
 *
 * @param status      OK, ERROR, DENIED, WAITING (e.g. a question awaiting a human), CANCELLED
 * @param output      text the agent will read (after the outbound safety review)
 * @param completedAt when the tool finished
 */
public record ToolResult(Status status, String output, Instant completedAt) {

    /** v0.0.18 🍊 Result status. */
    public enum Status { OK, ERROR, DENIED, WAITING, CANCELLED }

    /** v0.0.18 🍊 Successful result. */
    public static ToolResult ok(String output, Instant when) {
        return new ToolResult(Status.OK, output, when);
    }

    /** v0.0.18 🍊 Failed result with the reason. */
    public static ToolResult error(String reason, Instant when) {
        return new ToolResult(Status.ERROR, reason, when);
    }

    /** v0.0.18 🍊 Denied by a code-level guard. */
    public static ToolResult denied(String reason, Instant when) {
        return new ToolResult(Status.DENIED, reason, when);
    }

    /** v0.0.18 🍊 Started but waiting for someone (the real result arrives later as new input). */
    public static ToolResult waiting(String note, Instant when) {
        return new ToolResult(Status.WAITING, note, when);
    }
}
