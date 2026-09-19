# ai.yuzu.trace

> v0.0.12 🍊 Read side of the monitor history stored in `module_event` (written by `ai.yuzu.monitor`).

| Endpoint | Returns |
|---|---|
| `GET /api/agents/{agentId}/events?beforeSeq=&limit=100` | the agent's `ModuleEvent[]`, newest first; `beforeSeq` pages to older events, `limit` is clamped to 1–500; `BAD_REQUEST` for a malformed id, `NOT_FOUND` for an unknown agent (retired agents keep their history) |
| `GET /api/traces/{traceId}` | every event of the trace across agents, in time order (ties keep insertion order), at most 2,000; `BAD_REQUEST` for a malformed id, `[]` for an unknown trace |

- `TraceQueryService` — flushes the monitor's batch writer first (read-your-writes), then queries and maps to
  `ModuleEventView` (natural-language times).
- `AgentEventRepository` — agent-scoped (`AgentScopedRepository`): `SQL_*` statements bind `:agentId` and are
  clustered range scans on `PRIMARY KEY (agent_id, seq)`.
- `TraceEventRepository` — the one deliberate cross-agent read (a trace spans agents), by `KEY k_trace`.
- `ModuleEventRows` — row mapper; unknown module/phase names degrade to `SYSTEM`/`INFO` instead of failing.
- `TraceController` — the two endpoints above.

Note: the contract type `ModuleEvent` carries no `seq`, so clients cannot yet derive a `beforeSeq` cursor from
the events they hold; exposing `seq` needs a contract change.
