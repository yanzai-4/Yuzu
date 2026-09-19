# ai.yuzu.tool

> v0.0.28 🍊 Tools: "half AI, half code" — the AI decides arguments, code executes with its own guards.

| Sub-package | Responsibility |
|---|---|
| `spi` | `Tool`, `ToolSpec`, `ToolContext`, `ToolResult`, `Risk`, `ToolRegistry` (catalogs), `PermissionGuard`, `ApprovalGate` |
| `impl.chat` | `chat_post` |
| `impl.question` | `ask_user` (question cards, non-blocking) |
| `impl.memory` | `memory_read`, the memory-read module (time phrases resolved by code first, then by AI) |
| `impl.task` | `ticket_create`, `ticket_assign`, `task_approve`; room-member lookup and code-made notices |
| `impl.file` | `file_write`, `file_read` (chunks and line ranges), `file_list`; the workspace sandbox |
| `impl.mail` | `email_read`, `email_send` (always through an approval card) + `EmailApprovalHandler` |
| `impl.trade` | `market_quote`, `portfolio_read`, `trade_execute` (approval card above the threshold) + `TradeApprovalHandler` |
| `impl.web` | `web_browse` (untrusted: search and pages go through the outbound safety review) |
| `impl.code` | `code_write` (trusted: writes into `code/`, runs offline in `sandbox-exec`) |
| `impl.learn` | `learn`, the main consciousness's own door into habit memory (same de-duplication and conflict flow as the subconscious) |
