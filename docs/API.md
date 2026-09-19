# Yuzu 🍊 API contract (REST + SSE)

> v0.0.4 🍊 Frozen contract between the backend and the frontend. The TypeScript mirror lives in
> `frontend/src/api/types.ts`; change both together and bump the version.

## Conventions

- Base path `/api`; JSON everywhere; the Vite dev server proxies `/api` to `http://localhost:8080`.
- **Times** are natural-language strings (compact form `Sat Sep 19, 11:32:05 AM`), never epoch
  numbers. Ordering uses `seq` / event `id`.
- **Errors**: any non-2xx response has the body `ApiError { code, message, details, agentId?, time }`.
  Asynchronous failures arrive as `error` SSE events with the same shape. The UI shows a toast and
  appends the error to the Trace log.
- **Ids**: agents `agent-xxxx`, humans `user-xxxx`, rooms `room-xxxx`, records `<name>-<4hex>-<10hex>`.
  The default room is `room-0001`.

## Realtime stream

`GET /api/stream?roomId=room-0001&after=<cursor>` (SSE). The browser's automatic `Last-Event-ID`
header takes precedence over `after`.

Each SSE message has `event: <type>` and `data: EventEnvelope`:

```json
{ "id": 1789843986408123, "type": "chat.message", "roomId": "room-0001",
  "agentId": null, "time": "Sat Sep 19, 11:32:05 AM", "data": { } }
```

| type | data | notes |
|---|---|---|
| `hello` | `{ cursor, connectionId }` | first event of every connection (no SSE id) |
| `heartbeat` | `{ cursor }` | every 15 s (no SSE id) |
| `resync` | `{ cursor }` | the cursor was too old: call `/api/bootstrap` again |
| `chat.message` | `ChatMessage` | new or updated message (upsert by `id`) |
| `chat.delta` | `{ messageId, delta, streamState }` | streamed text appended to a message |
| `chat.card` | `Card` | question/approval card opened or updated |
| `chat.typing` | `{ agentId, typing }` | typing indicator |
| `user.joined` | `User` | |
| `agent.upsert` | `Agent` | created or edited |
| `agent.removed` | `{ agentId }` | retired |
| `agent.status` | `AgentStatus` | live desk state + bubble |
| `module.event` | `ModuleEvent` | trace event (START/STATE/END/ERROR/...) |
| `task.list` | `TaskListView` | an agent's current task list changed |
| `ticket.upsert` | `Ticket` | |
| `usage.tick` | `UsageSnapshot` | at most every 2 s |
| `sim.email` | `Email` | |
| `sim.trade` | `Trade` | |
| `sim.portfolio` | `Portfolio` | |
| `security.incident` | `Incident` | yellow notices, masks, guard denials |
| `settings.changed` | `LlmSettingsView` | |
| `error` | `ApiError` | asynchronous failure |

Client algorithm: `GET /api/bootstrap` → render snapshot → open the stream with
`after = snapshot.eventCursor` → apply every event with one reducer (upsert by id). On `resync`,
re-bootstrap.

## REST endpoints

### Session
| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/session/join` | `{ username }` | `User` (re-joining with the same name returns the same user) |
| GET | `/api/bootstrap?roomId=` | — | `Snapshot` |

### Chat and cards
| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/rooms/{roomId}/messages?beforeSeq=&limit=50` | — | `ChatMessage[]` (ascending) |
| POST | `/api/rooms/{roomId}/messages` | `{ userId, content }` | `ChatMessage` (mentions parsed server-side from `@Name` / `@all`) |
| POST | `/api/cards/{cardId}/answer` | `{ userId, optionIds: string[], otherText?: string }` | `Card` |

### Agents
| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/roles` | — | `RolePreset[]` |
| GET | `/api/rooms/{roomId}/agents` | — | `Agent[]` |
| POST | `/api/rooms/{roomId}/agents` | `CreateAgentRequest` | `Agent` (citrus name + avatar assigned automatically; max 8 → `AGENT_LIMIT`) |
| PATCH | `/api/agents/{agentId}` | `UpdateAgentRequest` | `Agent` |
| DELETE | `/api/agents/{agentId}` | — | `204` |
| POST | `/api/agents/{agentId}/pause` / `resume` / `interrupt` | — | `AgentStatus` |
| GET | `/api/agents/{agentId}/working-memory` | — | `WorkingMemoryView` |
| GET | `/api/agents/{agentId}/tasks` | — | `TaskListView` |
| GET | `/api/agents/{agentId}/events?beforeSeq=&limit=100` | — | `ModuleEvent[]` |

### Tickets and approvals
| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/rooms/{roomId}/tickets` | — | `Ticket[]` |
| POST | `/api/task-lists/{listId}/approve` | `{ userId }` | `TaskListView` (archives when the approver is allowed) |

### Settings (console)
| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/settings/llm` | — | `LlmSettingsView` |
| PUT | `/api/settings/llm` | `UpdateLlmSettingsRequest` | `LlmSettingsView` |
| PUT | `/api/settings/llm/key` | `{ apiKey }` | `LlmSettingsView` (key stored encrypted; only a mask is returned) |
| POST | `/api/settings/llm/test` | — | `LlmTestResult` |
| GET | `/api/settings/llm/models` | — | `string[]` |

### Usage, trace, simulation, demo
| Method | Path | Returns |
|---|---|---|
| GET | `/api/usage` | `UsageSnapshot` |
| GET | `/api/traces/{traceId}` | `ModuleEvent[]` |
| GET | `/api/sim/emails` | `Email[]` |
| GET | `/api/sim/trades` | `Trade[]` |
| GET | `/api/sim/portfolios` | `Portfolio[]` |
| POST | `/api/demo/seed?roomId=` | `Agent[]` (creates Yuzu/PM, Lime/Researcher, Kumquat/Engineer, Pomelo/Liaison if missing) |

## Types

See `frontend/src/api/types.ts` (authoritative field list). Enumerations:

- `Permission`: `CHAT_POST, CHAT_MENTION_ALL, ASK_USER, TASK_ASSIGN, TASK_APPROVE, WEB_BROWSE, EMAIL_READ,
  EMAIL_SEND, TRADE_VIEW, TRADE_EXECUTE, CODE_WRITE, CODE_EXECUTE, FILE_READ, FILE_WRITE, MEMORY_RECALL`
- `RoleKey`: `PROJECT_MANAGER, RESEARCHER, ENGINEER, CUSTOMER_LIAISON, FINANCE_ANALYST`
- `ModuleKind`: `CHAT, SAFETY, BEHAVIOR, HIGH_RISK, TOOL_CALLING, MONITOR, MAIN, PLANNING, COGNITION,
  SUBCONSCIOUS, LEARNING, MEMORY, WM_COMPACTOR, TOOL, SYSTEM`
- `Tier`: `IMPORTANT, DEFAULT, LIGHT`
