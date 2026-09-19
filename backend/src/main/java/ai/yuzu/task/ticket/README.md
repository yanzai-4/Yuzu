# ai.yuzu.task.ticket

> v0.0.29 🍊 Room-level tickets: requests turned into assignable work items with a checked lifecycle,
> plus the code-level ticket governor that stops private intake.

## Main classes

- `TicketService` — `create`, `assign`, `updateStatus`, `linkList`, `list(roomId)`, `get`. Every change runs in a
  transaction that reads the row `FOR UPDATE`, applies the rule, writes with an optimistic `version` check, and
  publishes `ticket.upsert` (payload `Ticket`) after commit. `syncWithList` is the system transition used by the
  task-list service.
- `TicketPolicy` — code-level permissions:
  - create / assign / cancel: humans, or agents holding `TASK_ASSIGN`;
  - `IN_PROGRESS` / `DONE`: the assignee, humans, or `TASK_ASSIGN` agents;
  - `APPROVED`: humans, or whoever assigned the ticket holding `TASK_APPROVE` (never the assignee). A ticket linked
    to a task list is approved through that list instead.
- `TicketStatus` — manual transitions (`canMoveTo`) and the transitions that follow a linked list (`following`).
- `TicketRepository` — the room-scoped `ticket` table (`PRIMARY KEY (room_id, seq)`). Ids are
  `ticket-<ownerHex>-<10hex>`: the owner is `agent-0000` for tickets created by humans, the creating agent otherwise.
- `Ticket` — contract DTO; `creator` and `assignedBy` are Java-only (`@JsonIgnore`).
- `TicketRow` — domain row (UTC instants, assigner, version). `NewTicket` — creation request.
- `TicketLinkTarget` — implemented by the task-list service so `linkList` updates both sides of the link.
- `TicketGovernor` (v0.0.29) — a `ChatMessageListener` that keeps intake honest **in code, not in a prompt**: when a
  coworker who may not assign work (`TASK_ASSIGN` missing) tells a human "on it, I'll do that", holds no live
  ticket and did not loop the project manager in, the PM @mentions that coworker once and asks for a ticket, and
  the PM's mind receives a governance notice (`NoticeService`) so it creates and assigns one.

## The ticket governor

```
coworker ──"@Alice on it, I'll build it"──▶ TicketGovernor (code checks, no model call)
   role ≠ PM and no TASK_ASSIGN ∧ @mentions a human ∧ not @the PM ∧ no ASSIGNED/IN_PROGRESS/DONE ticket
   ∧ commitment phrase ∧ no "ticket/assign/check with" ∧ causalDepth < LoopGuard.MAX_DEPTH ∧ room budget left
        ├─▶ chat: PM "@Kumquat please pause: there is no ticket for this yet …" (depth + 1, closure = false)
        └─▶ NoticeService ─▶ the PM's pool ("I should create a ticket and assign it")
coworker ──closure reply──▶ episode CLOSED: the governor is silent until a human speaks in the room again
```

Loop safety sits on top of the five existing layers: one warning per coworker and episode (`WARNED` → `CLOSED`),
the warning is counted in `LoopGuard`'s room budget, deep agent chains are left alone, and the structured
`closure` flag ends the exchange. The state map is guarded by a per-room `ReentrantLock`.

## Data flow

```
create(room, creator, NewTicket[.assignedTo]) ─▶ OPEN or ASSIGNED ─▶ ticket.upsert
assign(ticket, agent, actor)                  ─▶ ASSIGNED (assigned_by = actor, the default list publisher)
TaskListService.create(..., ticketId)         ─▶ syncWithList: link list, IN_PROGRESS
TaskListService.requestApproval               ─▶ DONE      (new work on the list moves it back to IN_PROGRESS)
TaskListService.approve                       ─▶ APPROVED
```

System transitions never fail a list change: tickets that ended, moved to another assignee or follow another list
are left untouched; unexpected errors are reported through the error sinks (UI toast).
