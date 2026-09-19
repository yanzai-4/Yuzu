# ai.yuzu.realtime

> v0.0.3 🍊 Server-Sent Events hub: every live update the frontend sees flows through here.

- `SseHub` — assigns monotonic cursors (boot-time millis × 1000, so they keep increasing across
  restarts), serializes each event once, keeps a 5,000-event replay ring (replayable types only) and
  fans events out to clients of the room (`"*"` = every room). Publishing and connecting share one short
  lock, so a reconnect gets replayed events followed by live ones with no gap or duplicate. Sends a
  heartbeat every 15 s.
- `ClientConnection` — one browser: bounded queue (2,048) + its own virtual writer thread. A full
  queue closes the connection; the browser reconnects with `Last-Event-ID` and replays.
- `EventSink` / `SseEmitterSink` — transport abstraction (SSE in production, in-memory in tests).
  Control events (`hello`, `heartbeat`, `resync`) carry no SSE id so they never move the cursor.
- `EventType` — wire names (`chat.message`, `agent.status`, `module.event`, ...) + replayability.
- `EventEnvelope` — JSON body `{id, type, roomId, agentId, time, data}`; `time` is natural language.
- `StreamController` — `GET /api/stream?roomId=&after=` (also honors the `Last-Event-ID` header).
- `ErrorReporter` — `ErrorSink` that turns asynchronous failures into `error` events (toast + trace).

A client whose cursor is older than the ring receives `resync` and must call `/api/bootstrap` again.
