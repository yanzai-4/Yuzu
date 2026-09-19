# ai.yuzu.agent.runtime

> v0.0.6 🍊 Per-agent runtime containers (isolation by agent id).

- `AgentRuntime` — owns every piece of mutable runtime state of one agent (profile snapshot, cancel
  token, pause flag; the consciousness pool, main loop, chat inbox and working memory are added in later
  steps). Module logic is shared and stateless; state never leaks between agents.
- `AgentRuntimeManager` — `ConcurrentHashMap<AgentId, AgentRuntime>`; starts runtimes at boot, reacts to
  hire/edit/pause/retire through `AgentLifecycleListener`, cancels in-flight work on shutdown.
