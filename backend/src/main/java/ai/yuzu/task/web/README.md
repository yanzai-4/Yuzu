# ai.yuzu.task.web

> v0.0.20 🍊 HTTP-facing adapters of the task system: REST endpoints and the bootstrap contribution.

## Main classes

- `TaskController`
  - `GET /api/rooms/{roomId}/tickets` → `Ticket[]` (latest 500, creation order; `NOT_FOUND` for unknown rooms).
  - `GET /api/agents/{agentId}/tasks` → `TaskListView` (`current` is `null` when there is no current list).
  - `POST /api/task-lists/{listId}/approve` body `{ userId }` → `TaskListView`. The "Approve & archive" button:
    any human of the agent's room may approve an `AWAITING_APPROVAL` list (`CONFLICT` otherwise,
    `PERMISSION_DENIED` for humans of other rooms, `NOT_FOUND` for unknown users or lists).
- `TaskSnapshotContributor` — adds `tickets` (the room's tickets) and `taskLists` (one `TaskListView` per
  present agent) to `GET /api/bootstrap`.

## Data flow

```
Browser ─REST─▶ TaskController ─▶ TicketService / TaskListService ─▶ MySQL
                                               └─▶ SSE ticket.upsert / task.list ─▶ every browser in the room
```

DTO field names match `frontend/src/api/types.ts` exactly; times are natural-language strings.
