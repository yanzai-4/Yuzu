# ai.yuzu.internal.memory

> v0.0.14 🍊 The agent's memories. Working memory now; habit and deep memory follow in later steps.

- `WorkingMemoryService` — a copy of the main consciousness's inputs (IN) and outputs (OUT), written only
  by the main loop. Idempotent (unique `origin_ref` = pool message id / run id); the main consciousness's
  own THINK message is stored once as OUT and skipped when it returns as an input (`emittedByMain`).
  Batched, single-flight compaction: at 20 verbatim entries the oldest are folded into the rolling digest by
  the `WorkingMemoryCompactor` and 10 stay verbatim, so prompts only grow by appends between compactions.
  Compacted rows remain in MySQL for time-range recall. The per-agent view is cached in memory.
- `WorkingMemoryRepository` — `working_memory_entry` / `working_memory_digest` (agent-scoped).
- `WorkingMemoryCompactor` — digest function; `WmCompactorModule` is the AI implementation (DEFAULT tier,
  template `wm_compactor`).
- `WorkingMemoryEntry`, `WorkingMemoryView` (contract type), `WorkingMemoryController`
  (`GET /api/agents/{agentId}/working-memory`).
