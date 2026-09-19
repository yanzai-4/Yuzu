# ai.yuzu.task

> v0.0.20 🍊 The task and ticket system: room-level tickets and every agent's ONE current task list.

## Responsibility

- **Tickets** (room level): the Project Manager (or a human) turns requests into tickets and assigns them
  to coworkers. Ticket lifecycle: `OPEN → ASSIGNED → IN_PROGRESS → DONE → APPROVED`, or `CANCELLED`.
- **Task lists** (agent level): every agent has one current list (`ACTIVE` or `AWAITING_APPROVAL`) plus
  archived history. The planning module proposes operations; this package applies them with code-level
  rules. Items are never deleted: they are checked (`DONE`) or struck through (`STRUCK`, with a reason).
- **Approval**: a list is archived only when every item is `DONE`/`STRUCK`, approval was requested, and the
  publisher (an agent holding `TASK_APPROVE`) or any human of the room approves. The planning side can never
  end a task flow by itself.

## Sub-packages

| Package | Main classes |
|---|---|
| `ai.yuzu.task` | `Actor` (HUMAN/AGENT, id, name), `ActorResolver` (verified actors, room membership, permission checks), `TaskText` (text normalization and limits) |
| `ticket` | `TicketService`, `TicketRepository`, `TicketStatus`, `Ticket` (contract DTO), `NewTicket`, `TicketLinkTarget` |
| `list` | `TaskListService`, `TaskOp`, `TaskListRepository`, `TaskItemRepository`, `TaskList` / `TaskItem` / `TaskListView` (contract DTOs) |
| `prompt` | `TaskPromptRenderer` (byte-stable prompt text of current and archived lists) |
| `web` | `TaskController` (REST), `TaskSnapshotContributor` (`/api/bootstrap`) |

Dependencies point one way: `web → list → ticket → ai.yuzu.task`, and `prompt → list`.

## Data flow

```
Human / PM agent ──create/assign──▶ TicketService ──ticket.upsert──▶ SSE (ticket board)
                                        ▲ syncWithList (IN_PROGRESS / DONE / APPROVED)
Planning module ──create / apply(TaskOp…) / requestApproval──▶ TaskListService ──task.list──▶ SSE
Publisher agent (Tickets tool) or human ("Approve & archive") ──approve──▶ TaskListService ──▶ archived
Planning prompt ◀── TaskPromptRenderer.renderCurrent(current(agentId)) + renderHistory(recentArchived(agentId, 3))
```

## Public API (called by other modules)

```java
// ai.yuzu.task.list.TaskListService
TaskListView current(AgentId agentId);
TaskList create(AgentId agentId, String goal, Actor publisher, String ticketId, List<String> items);
TaskList apply(AgentId agentId, List<TaskOp> ops);
TaskList apply(AgentId agentId, String expectedListId, List<TaskOp> ops);
List<String> validate(AgentId agentId, List<TaskOp> ops);          // dry run, problems as English sentences
TaskList requestApproval(AgentId agentId);
TaskListView approve(String listId, Actor approver);
List<TaskList> recentArchived(AgentId agentId, int limit);

// ai.yuzu.task.ticket.TicketService
Ticket create(String roomId, Actor creator, NewTicket request);
Ticket assign(String ticketId, AgentId assignee, Actor actor);
Ticket updateStatus(String ticketId, TicketStatus status, Actor actor);
Ticket linkList(String ticketId, String listId, Actor actor);
List<Ticket> list(String roomId);
Ticket get(String ticketId);

// ai.yuzu.task.prompt.TaskPromptRenderer
String renderCurrent(TaskListView view);
String renderHistory(List<TaskList> archived);
```

Actors come from `ActorResolver.human(userId)` / `agent(agentId)` or `Actor.of(profile | user)`; every
service re-verifies them (existence, room membership, not retired) before checking permissions.
