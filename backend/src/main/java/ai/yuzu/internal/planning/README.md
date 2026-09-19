# ai.yuzu.internal.planning

> v0.0.21 🍊 The planning module: keeps each agent's ONE current task list in step with new input.

Planning and cognition are two separate modules with their own prompts (no shared context). The intake
runs them in parallel, and the pool message is written only after both have finished.

- `TaskPlanner` — the interface the intake calls.
- `TaskPlannerService` — the implementation. It holds a per-agent planning lock across the LLM call; that
  is the only lock held across I/O, and inside it only the task service's own short locks are taken.
  It runs the module and then applies the decision in code:
  - CREATE: a new list. The publisher is resolved from its roster name, or taken from the ticket's
    assigner.
  - UPDATE: numbered operations are mapped to item ids and applied all-or-nothing.
  - An approval request, when every item is finished.
  It returns a first-person note ("I started a new task list …") that joins the stimulus in the pool.
- `PlanningModule` (DEFAULT tier, template `planning.md`) — sees the roster, its own profile, the last 3
  archived lists, the tickets assigned to it, its current list, its working memory and the input.
  Semantic checks:
  - CREATE only without a current list.
  - Publisher names must be real members.
  - Ticket ids must be its own.
  - Item numbers must exist.
  - Every operation passes `TaskListService.validate` (a dry run), so errors come back as retry
    feedback.
  If it fails, it degrades to NONE. It can never archive a list: only the publisher or a human approves.
- `PlanningDecision` — `reasoning, mode NONE|CREATE|UPDATE, goal?, items[], publisher?, ticketId?,
  ops[{op, item?, text?, goal?}], requestApproval`.
