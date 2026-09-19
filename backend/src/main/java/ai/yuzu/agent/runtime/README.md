# ai.yuzu.agent.runtime

> v0.0.30 🍊 Per-agent runtime containers (isolation by agent id) and the humans' controls over them.

- `AgentRuntime` — owns every piece of mutable runtime state of one agent (profile snapshot, cancel
  token, pause flag, its `Consciousness` (pool, main loop, subconscious scheduler); the chat inbox and
  working memory are added in later steps). Pausing pauses the main loop. Module logic is shared and stateless; state never leaks between agents.
- `AgentRuntimeManager` — `ConcurrentHashMap<AgentId, AgentRuntime>`; starts runtimes at boot, reacts to
  hire/edit/pause/retire through `AgentLifecycleListener`, cancels in-flight work on shutdown.
- `AgentContext` — immutable per-invocation snapshot (agent, room, profile, trace, parent span, cancel token,
  the current time captured once); created with `AgentRuntime.context(traceId, parentSpanId, time)`.
- `AgentComponent` / `AgentComponentFactory` — feature packages contribute per-agent stateful components
  (for example the chat inbox); the manager creates one per runtime and forwards profile changes/shutdown.
- `AgentControlService` — what a human presses: `interrupt`, `pause`, `resume` for one coworker and
  `stopAll` / `resumeAll` for a whole room (serialized per room with a `ReentrantLock`). Returns the live
  `AgentStatusView` (or a `RoomControlView`) so the caller sees the result immediately.
- `RoomControlView` — contract type `RoomControlResult` (`roomId`, `action`, `affected`, `statuses`, `time`).

## How cancellation works (the one-second promise)

`AgentRuntime.interrupt(reason)` atomically swaps in a fresh `CancelToken` and cancels the old one:

1. Every thread bound to the old token is interrupted, which aborts a blocking `HttpClient` read at once.
2. `LlmExecutor` wraps the stream sink with `StreamSink.guarded(...)`, and `OpenAiCompatibleProvider`
   re-checks the token per SSE line, so a stream that keeps arriving still stops at the next chunk.
3. Modules turn the resulting `CancelledException` into `span.cancelled(...)`, so no span stays open.
4. `MainLoop` catches it and `ConsciousnessPool.abandon()` releases ownership, so the pool is never stuck
   "running" — the next message starts a new run on the fresh token.
5. `AgentControlService` reports a SYSTEM span that ends **CANCELLED**, so the trace waterfall, the desk
   bubbles and every connected client see what the human did. Pause/resume additionally persist the state
   through `AgentService`, which publishes `agent.upsert`.
