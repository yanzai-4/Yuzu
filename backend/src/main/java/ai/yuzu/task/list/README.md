# ai.yuzu.task.list

> v0.0.20 🍊 Every agent's ONE current task list with progress, plus its archived history.

## Main classes

- `TaskListService` — `current`, `create`, `apply`, `validate`, `requestApproval`, `approve`, `recentArchived`.
  - **One current list**: `create` refuses while a list is `ACTIVE` or `AWAITING_APPROVAL`; the V3 unique key
    `(agent_id, open_slot)` enforces the same rule in MySQL.
  - **Concurrency**: every write for an agent runs under that agent's `ReentrantLock` (`AgentLocks`, bounded
    wait, never `synchronized`) inside one transaction whose list `UPDATE` checks the optimistic `version`, so
    concurrent planning calls are serialized and never lose updates.
  - **Cache**: the `TaskListView` of each agent is cached (Caffeine) and replaced after every committed change,
    which also publishes `task.list` with the new view.
  - **Tickets**: a list created for a ticket links it and moves it along (`IN_PROGRESS` → `DONE` → `APPROVED`).
- `TaskOp` — sealed operations with a JSON `op` discriminator: `ADD(text)`, `START(itemId)`,
  `CHECK(itemId, note)`, `STRIKE(itemId, reason)`, `EDIT_GOAL(goal, reason)`, `NOTE(itemId, note)`.
- `TaskListDraft` — the rules. Operations run in order on a working copy; each invalid one is recorded, and a
  batch with any problem changes nothing (the exception type follows the first problem: `NOT_FOUND` unknown item,
  `CONFLICT` invalid transition, `BAD_REQUEST` malformed input; `details.problems` lists every sentence).
  - `START`: TODO → DOING (DOING stays); DONE and STRUCK items cannot restart.
  - `CHECK`: TODO/DOING → DONE; re-checking is idempotent; STRUCK items cannot be checked.
  - `STRIKE`: any item, reason required (DONE included); re-striking keeps the first reason.
  - `ADD` / `EDIT_GOAL`: new work sends an `AWAITING_APPROVAL` list back to `ACTIVE`; duplicates of unfinished items
    are refused; an edited goal is kept in `goal_history` with its reason.
  - `NOTE`: appends to the item's notes ("; "), never repeating a note already present.
  - Limits: 50 items, 50 operations per call, 500 characters per item text, note part or reason, 1,000 per goal.
- `ApprovalPolicy` — humans of the room always; an agent only if it is the list's publisher, not its owner, and
  holds `TASK_APPROVE`. Anyone else gets `PERMISSION_DENIED`; a list that is not `AWAITING_APPROVAL` gets `CONFLICT`.
- `OutcomeWriter` — the stored "how it went" sentence, e.g. `Completed with changes: 3 of 4 items done, 1 struck.
  Approved by Alice (human).`
- `TaskListStore` — aggregate persistence (list + items) and view loading. `TaskListRepository` and
  `TaskItemRepository` extend `AgentScopedRepository`; every `SQL_*` constant binds `:agentId`.
- DTOs matching `frontend/src/api/types.ts`: `TaskListView`, `TaskList`, `TaskItem`. `TaskList` also carries
  Java-only `publisherKind` and `previousGoals` for prompts (`@JsonIgnore`).

## Data flow

```
Planning ─▶ create(agent, goal, publisher | ticket's assigner, ticketId, items)
         ─▶ apply(agent, [ADD, START, CHECK, STRIKE, EDIT_GOAL, NOTE])   (all or nothing; validate() = dry run)
         ─▶ requestApproval(agent)          (every item DONE or STRUCK)  ─▶ AWAITING_APPROVAL
Publisher agent / human ─▶ approve(listId, approver) ─▶ ARCHIVED with outcome; the current list is cleared
Every committed change ─▶ cache ─▶ SSE task.list (TaskListView)
```
