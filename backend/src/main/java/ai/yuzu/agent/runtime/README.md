# ai.yuzu.agent.runtime

> v0.0.14 🍊 Per-agent runtime containers (isolation by agent id).

- `AgentRuntime` — owns every piece of mutable runtime state of one agent (profile snapshot, cancel
  token, pause flag, its `Consciousness` (pool, main loop, subconscious scheduler); the chat inbox and
  working memory are added in later steps). Pausing pauses the main loop. Module logic is shared and stateless; state never leaks between agents.
- `AgentRuntimeManager` — `ConcurrentHashMap<AgentId, AgentRuntime>`; starts runtimes at boot, reacts to
  hire/edit/pause/retire through `AgentLifecycleListener`, cancels in-flight work on shutdown.
- `AgentContext` — immutable per-invocation snapshot (agent, room, profile, trace, parent span, cancel token,
  the current time captured once); created with `AgentRuntime.context(traceId, parentSpanId, time)`.
