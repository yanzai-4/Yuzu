# ai.yuzu.tool

> v0.0.22 🍊 Tools: "half AI, half code" — the AI decides arguments, code executes with its own guards.

| Sub-package | Responsibility |
|---|---|
| `spi` | `Tool`, `ToolSpec`, `ToolContext`, `ToolResult`, `Risk`, `ToolRegistry` (catalogs), `PermissionGuard` |
| `impl.chat` | `chat_post` |
| `impl.question` | `ask_user` (question cards, non-blocking) |
| `impl.memory` | `memory_read`, the memory-read module (time phrases resolved by code first, then by AI) |
| `impl.task` | `ticket_create`, `ticket_assign`, `task_approve`; room-member lookup and code-made notices |
| `impl.web` | `web_browse` (untrusted: search and pages go through the outbound safety review) |
| `impl.code` | `code_write` (trusted: writes into `code/`, runs offline in `sandbox-exec`) |
| `impl.*` (next) | learning, e-mail, trading, files |
