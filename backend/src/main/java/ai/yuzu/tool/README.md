# ai.yuzu.tool

> v0.0.18 🍊 Tools: "half AI, half code" — the AI decides arguments, code executes with its own guards.

| Sub-package | Responsibility |
|---|---|
| `spi` | `Tool`, `ToolSpec`, `ToolContext`, `ToolResult`, `Risk`, `ToolRegistry` (catalogs), `PermissionGuard` |
| `impl.*` | Concrete tools (`chat` so far; question cards, tickets, memory, web, e-mail, trading, code, files next) |
