# ai.yuzu.tool.impl.task

> v0.0.22 🍊 Code-enforced ticket assignment and task-list approval tools for Project Manager coworkers.

- `TicketCreateTool` — creates a room ticket, optionally resolves an assignee and requester by exact display name,
  and notifies an immediate assignee.
- `TicketAssignTool` — assigns an open ticket to a named AI coworker and delivers a trusted code notice through
  intake.
- `TaskApproveTool` — archives a finished task list only when the acting coworker is its authorized publisher, then
  notifies the owner.
- `TicketSupport` — shared case-insensitive room-member lookup and notice rendering.

Every public tool re-checks `TASK_ASSIGN` or `TASK_APPROVE` through `PermissionGuard`, even when code calls the tool
without `ToolDispatcher`. `TicketService` and `TaskListService` remain the policy authorities for lifecycle and
publisher checks.
