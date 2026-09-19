# ai.yuzu.tool.spi

> v0.0.18 🍊 The tool plug-in interface.

- `Tool<A>` — `spec()` + `execute(ToolContext, A args)`; implementations re-check permissions themselves
  (defense in depth: bypassing the dispatcher still fails).
- `ToolSpec<A>` — name, description, argument record (strict schema), required permissions, `Risk`, timeout,
  `trusted` (code-only output skips the AI outbound review), first-person result source.
- `ToolRegistry` — lookup by name; renders the permitted-tool catalog (main consciousness) and the full
  catalog with argument schemas (tool calling); validates arguments. Implements `ToolCatalogProvider`.
- `PermissionGuard` — code-level permission checks; denials become GUARD security incidents.
- `ApprovalGate` — layer 4 of the high-risk chain: opens an Approve / Reject card (`CardService`) for a tool
  call and lets the tool return `WAITING`, so the rest of the batch is not held back. The
  `CardAnswerHandler` registered for the card's purpose finishes the work; `ApprovalGate.approved(answer)`
  is the only way to read the decision, and only the literal "Approve" option lets the work continue.
- `ToolContext`, `ToolResult` (OK / ERROR / DENIED / WAITING / CANCELLED + finish time), `Risk`.
