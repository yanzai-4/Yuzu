# ai.yuzu.external.toolcall

> v0.0.18 🍊 From decided actions to tool results back in the mind.

- `ActionPipeline` (the `ActionSubmitter`) — behavior review ∥ tool-call decomposition (nothing executes
  before the review passes) → high-risk second review → `ToolDispatcher` → outbound safety review of
  untrusted outputs (only problematic passages are masked) → results with finish times + infeasible actions
  at the end → `IntakePipeline.deliver` (planning ∥ cognition → pool). Rejections put a REVIEW warning in the
  pool; 3 in a row post a yellow notice instead (loop breaker).
- `ToolCallingModule` (DEFAULT tier, template `tool_calling`, full tool catalog in S1) — natural-language
  actions → `{actionIndex, tool, argsJson}` calls; code validates tools, arguments and coverage.
- `ToolDispatcher` — every call in parallel on a virtual thread: argument schema check, code-level
  permission check, timeout, monitor span, persisted `tool_call` row; failures become results.
- `ActionTracker` — per-agent actions in progress + rejection streak; `ActionBatchRepository`,
  `ToolCallRepository`, `ToolPlan`.
