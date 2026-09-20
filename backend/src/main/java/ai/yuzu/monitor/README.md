# ai.yuzu.monitor

> v0.0.12 🍊 Every agent module reports here. The monitor keeps each agent's live desk state (the bubble above
> its head) and its full history (`module_event` table + the `module.event` stream).

## Reporting API (for module authors)

Inject `MonitorService` (use `MonitorService.noop()` in unit tests: it reports nothing but still hands out trace
ids). Every call is non-blocking and cheap, and **never throws** — reporting can never break an agent.

| Call | Effect |
|---|---|
| `Span start(agentId, module, text, traceId, parentSpanId)` | START; a null `traceId` starts a new trace `trace-<agentHex>-<10hex>` |
| `Span start(agentId, module, text)` | root span in a new trace |
| `span.state(text)` | STATE: what the module is doing right now (feeds the bubble) |
| `span.desk(DeskState)` | overrides the desk state the span implies (see below) |
| `span.detail(key, value)` | attached to the terminal event (a sanitized, JSON-safe copy; at most 29 keys, so `durationMs`, `errorType` and `errorCode` always fit) |
| `span.child(module, text)` | nested span: same agent, same trace, parent = this span |
| `span.end(text)` / `span.fail(Throwable)` / `span.fail(reason)` / `span.cancelled(reason)` | exactly one terminal event carrying `durationMs`; later calls are ignored |
| `span.close()` | END "done" unless already finished (try-with-resources) |
| `span.traceId()` / `span.spanId()` | ids to hand to downstream work |
| `info(agentId, module, text, detail[, traceId, parentSpanId])` | one-off INFO event |
| `setPoolSize` / `setPendingBatches` / `setWaiting(agentId, waiting, reason)` / `setPaused` | live counters and flags on the desk |

```java
Span span = monitor.start(agentId, ModuleKind.TOOL, "Searching the web for yuzu prices", traceId, parentSpanId);
try {
    span.state("Reading 3 result pages").detail("query", query);
    span.end("Found 3 sources");
} catch (RuntimeException e) {
    span.fail(e);   // CancelledException / InterruptedException become CANCELLED
    throw e;
}
```

`try (Span span = ...)` is fine for code that cannot fail, but a span closed by try-with-resources while an
exception propagates records END "done": call `fail(e)` before leaving the block. Hand `span.traceId()` to
downstream work (pool messages, chat posts, other agents) and `span.spanId()` as the parent of nested work, so
`GET /api/traces/{traceId}` shows the whole causal chain across agents. `fail(Throwable)` shows the message of a
`YuzuException` and only the type of any other exception (no secrets reach the UI); `errorType`/`errorCode` go to
the detail.

## Desk state

| Module | Label | Desk state while a span runs |
|---|---|---|
| `MAIN`, `PLANNING`, `COGNITION`, `SUBCONSCIOUS` | Main consciousness, Planning, Cognition, Subconscious | THINKING |
| `TOOL`, `TOOL_CALLING` | Tool, Tool calling | WORKING |
| `SAFETY`, `BEHAVIOR`, `HIGH_RISK` | Safety review, Behavior review, High-risk review | WORKING |
| `CHAT` | Chat | WORKING while triaging; **TALKING** once the span declares `span.desk(DeskState.TALKING)` while posting |
| `LEARNING`, `MEMORY`, `WM_COMPACTOR`, `SYSTEM` | Learning, Memory, Working-memory compactor, System | WORKING |
| `MONITOR` | Monitor | quiet: traced, never shown on the desk (the summarizer's own work) |

A span may also declare `WAITING` (for example an ask-user tool waiting for a card answer) or `IDLE` (quiet);
the `setWaiting` flag is the other way to show WAITING. The derived state is
**ERROR** (sticky for 5 s after a failure) > **PAUSED** > **WAITING** > **TALKING** > **THINKING** > **WORKING** >
**IDLE**. Several spans of one module (for example parallel subconscious threads) keep it active until the last
one ends; `activeModules` lists every running module once, most relevant first.

## Bubble and summarizer

- `bubble.module` is the label of the focus span (highest desk state, then module relevance, then most recent);
  `bubble.summary` is its latest START/STATE text on one line, at most 80 characters (the code default).
- ERROR shows the failed module and message, PAUSED "Taking a break.", WAITING the reason ("Waiting for a
  human." by default) and IDLE "Idle — waiting for something to do.".
- `BubbleSummarizer` is the plug-in point for a smarter summary (the LIGHT-tier monitor AI): register a bean with
  `@Primary`. The board calls it on a virtual thread, at most once every 4 s per agent, only after something
  changed and never while idle, with the last 20 events and the code default. A result that differs from the
  default is shown while the same busy period and focus module last; returning the default (or null) keeps the
  live code text. `CodeBubbleSummarizer` (the default bean) returns the code default. The AI summarizer should
  report its own work as `MONITOR` spans, which never change the desk.

## Realtime, bootstrap and REST

- `module.event` (replayable) for every event, to the agent's room. Platform events of `agent-0000` go to every
  room; events of an agent the registry does not know are stored but never streamed.
- `agent.status` (`AgentStatusView`, not replayable): throttled per agent to at most one per 250 ms (leading edge
  immediate, trailing edge carries the latest state); identical statuses are skipped, and every status is re-sent
  every 15 s so a client that reconnected with `Last-Event-ID` (statuses are not replayed) converges. The timer
  thread only hands off; publishing runs on virtual threads.
- `/api/bootstrap` statuses come from `AgentStatusBoard.snapshot(agentIds)`; `POST /api/agents/{id}/pause`,
  `/resume` and `/interrupt` return `AgentStatusBoard.status(id)`.
- Present agents are registered on the board when the application is ready (and on hire), so reporting never
  needs a registry lookup; the lookup only remains as a cached fallback for retired or unknown agents.

## Persistence

`ModuleEventStore` queues events into a `BatchWriter` (flush every 200 ms or per 200 rows). Text is clipped to
1000 characters, details are stored as JSON (at most 32 entries per level, depth 4, 16k characters of strings);
`INSERT IGNORE` makes a duplicate random record id cost one row instead of a whole batch. Rows can lag up to
~200 ms behind the stream; trace reads call `flush()` first. History queries live in `ai.yuzu.trace`.

## Classes

| Class | Responsibility |
|---|---|
| `MonitorService`, `DefaultMonitorService`, `NoopMonitorService` | reporting API; dispatch = persist (async) + status board + SSE, each step isolated |
| `Span`, `SpanHandle`, `NoopSpan`, `SpanEmitter` | span handles (one lock per span serializes its events) |
| `ModuleKind`, `DeskState`, `EventPhase` | contract enums with labels, desk states and priorities |
| `ModuleEvent`, `ModuleEventView` | domain event and contract shape `ModuleEvent` |
| `ModuleEventFactory`, `DetailSanitizer`, `TextClip`, `BubbleText`, `TraceIds` | event building: ids, clipping, JSON-safe details, bubble texts |
| `ModuleEventStore` | batched asynchronous INSERTs into `module_event` |
| `AgentStatusBoard`, `AgentLiveState`, `StatusDeriver`, `ActiveSpan`, `CoalescingTrigger`, `MonitorTimings` | live state per agent, derivation rules, throttled publishing and summarizing |
| `BubbleSummarizer`, `CodeBubbleSummarizer` | bubble summary plug-in point and its code default |
| `AgentLookup`, `AgentServiceLookup` | the monitor's view of the agent registry (room, paused, retired) |
| `MonitorAgentListener` | startup preload + hire / edit / pause / retire → board registration and SYSTEM events |
| `AgentStatusView` | contract type `AgentStatus` |

- `MonitorModuleReporter` (v0.0.34) — adapter from `module.ModuleReporter` to this service.