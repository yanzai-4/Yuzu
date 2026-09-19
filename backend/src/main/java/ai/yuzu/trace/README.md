# ai.yuzu.trace

> v0.0.12 🍊 Read side of the monitor history stored in `module_event` (written by `ai.yuzu.monitor`).

| Endpoint | Returns |
|---|---|
| `GET /api/agents/{agentId}/events?beforeSeq=&limit=100` | the agent's `ModuleEvent[]`, newest first; `beforeSeq` pages to older events, `limit` is clamped to 1–500; `BAD_REQUEST` for a malformed id, `NOT_FOUND` for an unknown agent (retired agents keep their history) |
| `GET /api/traces/{traceId}` | every event of the trace across agents, in time order (ties keep insertion order), at most 2,000; `BAD_REQUEST` for a malformed id, `[]` for an unknown trace |
| `GET /api/traces/{traceId}/llm-calls` | `LlmCall[]`: every recorded HTTP attempt of the trace, oldest first, at most 500 (metadata only) |
| `GET /api/llm-calls/{callId}/payload` | `LlmCallPayload`: the exact request and response JSON of one attempt; `NOT_FOUND` when the attempt failed before a payload was written, when the file is gone or when it is larger than 4 MB |

- `TraceQueryService` — flushes the monitor's batch writer first (read-your-writes), then queries and maps to
  `ModuleEventView` (natural-language times).
- `AgentEventRepository` — agent-scoped (`AgentScopedRepository`): `SQL_*` statements bind `:agentId` and are
  clustered range scans on `PRIMARY KEY (agent_id, seq)`.
- `TraceEventRepository` — the one deliberate cross-agent read (a trace spans agents), by `KEY k_trace`.
- `ModuleEventRows` — row mapper; unknown module/phase names degrade to `SYSTEM`/`INFO` instead of failing.
- `LlmCallRepository` — the second deliberate cross-agent read: `llm_call` rows of one trace (`KEY k_trace`)
  and one row by id. The workspace path of a payload stays server-side.
- `LlmCallInspector` — flushes the recorder, maps rows to `LlmCallView` and reads one payload file from
  `workspaces/<agentId>/llm/<date>/<callId>.json` (path-traversal check, 4 MB cap). Payloads never contain
  credentials: the API key only travels in an HTTP header, which is not recorded.
- `LlmCallView` / `LlmCallPayloadView` — contract types `LlmCall` and `LlmCallPayload`. A UI matches a span to
  its calls by `traceId` + `agentId` + `module` (`LlmCall.module` is the same name as `ModuleEvent.module`).
- `TraceController` — the four endpoints above.

Note: the contract type `ModuleEvent` carries no `seq`, so clients cannot yet derive a `beforeSeq` cursor from
the events they hold; exposing `seq` needs a contract change.
