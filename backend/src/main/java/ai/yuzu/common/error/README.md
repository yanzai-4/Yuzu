# ai.yuzu.common.error

> v0.0.1 🍊 The error model shared by REST responses and realtime error events.

- `ErrorCode` — stable codes (the UI switches on them) with HTTP status and default message.
- `YuzuException` — base of every expected failure; fluent `with(key, value)` details and
  `forAgent(id)`. Subclasses: `NotFound`, `Conflict`, `BadRequest`, `AgentLimit`, `PermissionDenied`,
  `SandboxViolation`, `SecurityBlocked`, `NotConfigured`, `LlmAuth`, `LlmOutputInvalid`,
  `ToolExecution`, `Cancelled`, `BudgetExhausted`.
- `ApiError` — JSON body `{code, message, details, agentId, time}`.
- `GlobalExceptionHandler` — maps exceptions thrown by controllers to `ApiError` responses.

Rule: risky functions throw a specific subclass; never swallow an exception silently.
