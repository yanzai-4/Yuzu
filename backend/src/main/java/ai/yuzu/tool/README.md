# ai.yuzu.tool

> v0.0.19 🍊 Tools: "half AI, half code" — the AI decides arguments, code executes with its own guards.

| Sub-package | Responsibility |
|---|---|
| `spi` | `Tool`, `ToolSpec`, `ToolContext`, `ToolResult`, `Risk`, `ToolRegistry` (catalogs), `PermissionGuard` |
| `impl.chat` | `chat_post` |
| `impl.question` | `ask_user` (question cards, non-blocking) |
| `impl.memory` | `memory_read`, the memory-read module (time phrases resolved by code first, then by AI) |
| `impl.*` (next) | tickets, learning, web, e-mail, trading, code, files |
