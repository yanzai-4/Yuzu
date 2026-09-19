# features/insights/trace

> v0.0.30 🍊 The Trace tab: what every coworker is doing, what it cost, and the controls to stop it.

## Layout

```
TraceTab
├── TraceControls          interrupt / pause / resume one coworker · stop all / resume all
└── Live | History | Incidents | Errors
    ├── EventStream        live `module.event` feed (virtualized, filters, freeze, clear)
    ├── AgentHistory       one coworker's stored history, paged with the X-Next-Cursor header
    ├── IncidentList       security incidents
    └── ErrorList          failed requests and asynchronous errors
TraceWaterfall (modal, opened by any trace id)
├── SpanRow[]              spans nested by parentSpanId, module colors, timeline bars
└── EventInspector         the selected event + the model calls behind it + their raw JSON
```

## Files

| File | Responsibility |
|---|---|
| `TraceTab.tsx` | container: controls + the four views |
| `TraceControls.tsx` | the human's controls; optimistic, rolled back on failure (`features/agents/agentActions`) |
| `EventStream.tsx`, `EventRow.tsx`, `TraceFilters.tsx` | the live feed |
| `AgentHistory.tsx` | agent picker, "Load older events", reload |
| `TraceWaterfall.tsx`, `spanTree.ts` | `GET /api/traces/{id}` merged with live events, grouped into spans |
| `EventInspector.tsx` | one event's text, ids and detail; its `LlmCall`s; the raw request/response on demand |
| `IncidentList.tsx`, `ErrorList.tsx` | incidents and the error log |
| `useTraceData.ts` | store selectors, `useAgentHistory` (cursor paging), `useTraceLlmCalls`, `llmCallsOf` |

## Notes

- **Paging** never invents a cursor: `listAgentEventPage` returns the `X-Next-Cursor` header of the previous
  page and the button disappears when the server stops sending one.
- **Matching a span to its model calls**: the exact call when the event's detail names an `llmCallId`,
  otherwise every call of the same agent and module inside the trace (`LlmCall.module` uses the same names
  as `ModuleEvent.module`).
- **Payloads are fetched per call**, only when the user expands "Raw request / response": a prompt can be
  hundreds of kilobytes. They never contain credentials.
- **Controls** apply optimistically so a desk reacts instantly; the answer's `AgentStatus` replaces the guess
  and any failure restores the previous state (the HTTP layer already toasts the error once). Interrupting a
  coworker stops its streamed answer within a second.
