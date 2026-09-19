# ai.yuzu.agent

> v0.0.6 🍊 Agents as employees: identity, job, persona, permissions and limits.

- `Role` — five presets (Project Manager, Data Researcher, Software Engineer, Customer Liaison, Finance
  Analyst) with title, work scope, persona, default permissions and limits. The PM persona owns intake
  and stops coworkers who pick up unassigned human requests.
- `Permission` / `Limits` / `PermissionScope` — what an agent may do (15 permissions, high-risk ones
  flagged) plus numeric limits; `describe()` renders them into prompts, `hash()` keys caches.
- `CitrusCatalog` — 16 citrus identities (name, avatar key, color); names are assigned automatically.
- `AgentProfile` / `AgentView` — immutable profile (prompt `describe()`) and its API shape.
- `AgentService` — hire (max 8 per workgroup, per-room lock), edit (optimistic locking), pause/resume,
  retire (frees the name). Caffeine caches by id and room; also the agent `RoomMemberSource`.
  Notifies `AgentLifecycleListener`s and publishes `agent.upsert` / `agent.removed`.
- `AgentRepository` — the `agent` registry table.
- `AgentController` — `/api/roles`, `/api/rooms/{roomId}/agents`, `/api/agents/{id}` (PATCH/DELETE) and
  `/pause`, `/resume`, `/interrupt`.
- `AgentSnapshotContributor` — agents and their statuses for `/api/bootstrap`.
