package ai.yuzu.external.toolcall;

import ai.yuzu.llm.structured.Desc;

import java.util.List;

/** v0.0.18 🍊 Output of the tool-calling module: concrete calls plus actions that cannot be done. */
public record ToolPlan(
        @Desc("Brief reasoning about how the actions map to tools") String reasoning,
        @Desc("Tool calls; each belongs to an action index") List<Call> calls,
        @Desc("Actions that no permitted tool can perform, with the reason") List<Infeasible> infeasible) {

    /** v0.0.18 🍊 One tool call; argsJson is a JSON object (as a string) matching the tool's arguments. */
    public record Call(int actionIndex, String tool, String argsJson) {
    }

    /** v0.0.18 🍊 An action that cannot be executed. */
    public record Infeasible(int actionIndex, String reason) {
    }
}
