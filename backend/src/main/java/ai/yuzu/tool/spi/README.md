# ai.yuzu.tool.spi

> v0.0.18 🍊 The tool plug-in interface.

- `Tool<A>` — `spec()` + `execute(ToolContext, A args)`; implementations re-check permissions themselves
  (defense in depth: bypassing the dispatcher still fails).
- `ToolSpec<A>` — name, description, argument record (strict schema), required permissions, `Risk`, timeout,
  `trusted` (code-only output skips the AI outbound review), first-person result source.
- `ToolRegistry` — lookup by name; renders the permitted-tool catalog (main consciousness) and the full
  catalog with argument schemas (tool calling); validates arguments. Implements `ToolCatalogProvider`.
- `PermissionGuard` — code-level permission checks; denials become GUARD security incidents.
- `ToolContext`, `ToolResult` (OK / ERROR / DENIED / WAITING / CANCELLED + finish time), `Risk`.
