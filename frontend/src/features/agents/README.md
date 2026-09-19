# features/agents

> v0.0.30 🍊 Hiring, inspecting and controlling the citrus coworkers.

| File | Responsibility |
|---|---|
| `AgentsDialog.tsx`, `HireAgentForm.tsx`, `AgentRoster.tsx` | the roster and the hire dialog (max 8 → `AGENT_LIMIT`) |
| `AgentInspector.tsx` | the drawer of one coworker: live status, tasks, profile, permissions, memory |
| `InspectorControls.tsx` | Pause / Resume / Interrupt and Retire (with an inline confirmation) |
| `InspectorSettings.tsx`, `PermissionChecklist.tsx`, `LimitsEditor.tsx`, `permissions.ts` | scope editing |
| `InspectorMemory.tsx` | working memory |
| `agentActions.ts` | every write: `hireAgent`, `patchAgent`, `controlAgent`, `controlRoom`, `retire`, `seedDemoTeam` |

## Controls (v0.0.30)

`controlAgent(agentId, 'pause' \| 'resume' \| 'interrupt')` and `controlRoom('stop-all' \| 'resume-all')`
apply the new state **optimistically** (`applyLocal('agent.upsert', …)`), then replace the guess with the
`AgentStatus` the backend returns. A failure restores the previous state; the toast comes from the HTTP
layer, which reports every failed request exactly once. The same actions are used by the office inspector
and by the Trace tab's control bar.
