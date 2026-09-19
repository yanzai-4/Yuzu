# ai.yuzu.tool

> v0.0.22 🍊 Tools: "half AI, half code" — the AI decides arguments, code executes with its own guards.

| Sub-package | Responsibility |
|---|---|
| `spi` | `Tool`, `ToolSpec`, `ToolContext`, `ToolResult`, `Risk`, `ToolRegistry` (catalogs), `PermissionGuard` |
| `impl.chat` | `chat_post` |
| `impl.question` | `ask_user` (question cards, non-blocking) |
| `impl.memory` | `memory_read`, the memory-read module (time phrases resolved by code first, then by AI) |
| `impl.task` | `ticket_create`, `ticket_assign`, `task_approve`; room-member lookup and code-made notices |
| `impl.*` (next) | learning, web, e-mail, trading, code, files |
