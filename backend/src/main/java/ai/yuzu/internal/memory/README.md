# ai.yuzu.internal.memory

> v0.0.28 🍊 The agent's memories: working memory (the main loop's copy) plus the two long-term memories.

## Working memory

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

## Long-term memory (one flow, two stores)

Habit memory is "how I work" (written by the learning module, read by cognition); deep memory is "what I
know" (written by the memory module, read on demand by the `memory_read` tool). Both are written through
`MemoryWriter`, which spends a model call only where code cannot decide:

1. `MemoryHash` over the normalized candidate — an exact repeat is dropped against
   `UNIQUE(agent_id, content_hash)` with no model call at all;
2. FULLTEXT ngram search for similar entries — nothing similar means the entry is simply new, again with no
   model call (so an agent's first habits and memories are free);
3. `MemoryJudgeModule` (DEFAULT tier, template `memory_judge`) returns `MemoryJudgement`
   (NEW / DUPLICATE / CONFLICT, plus MERGE / OVERWRITE / IGNORE);
4. DUPLICATE is merged into the existing entry, overwrites it (old row → `SUPERSEDED`) or is ignored;
5. CONFLICT writes nothing: the old entry stays and the contradiction is held for
   `MemoryWriter.HOLD_ROUNDS` (10) subconscious rounds by `internal.subconscious.ConflictTracker`.

- `LearningModule.learn(ctx, name, scenario, technique)` — habit memory. Callers: the subconscious and the
  `learn` tool. Never throws; a failure becomes an `IGNORED` outcome.
- `MemoryModule.remember(ctx, title, content, keywords, source)` — deep memory. Same contract.
- `HabitMemoryService` / `DeepMemoryService` — the `MemoryStore` implementations. Each owns its table and
  its readers' caches: `HabitMemoryService` invalidates `internal.cognition.HabitIndexService` inside every
  write, `DeepMemoryService` has no read cache, so recall sees a memory the moment it is written.
- `MemoryConflictRepository` / `MemoryConflict` — `memory_conflict` rows with both copies as plain JSON (no
  time fields), the round countdown and OPEN / RESOLVED / EXPIRED.
- `MemoryCandidate`, `StoredMemory`, `MemoryKind`, `MemoryOutcome`, `MemoryHash`, `MemoryStore` — the small
  types that let the habit and deep flows be literally the same code.

### Why the judge never overwrites on doubt

A wrong merge loses a sentence; a wrong overwrite loses a memory, and a wrong "CONFLICT resolved" makes the
agent change its mind because of one stray message. So: conflicts keep the old entry, expiry keeps the old
entry, and a judge that is unavailable degrades to NEW (nothing is lost, and the content hash still stops
exact repeats).

## Change log

- v0.0.28 — habit and deep memory, the shared write flow, the judge and conflicts.
- v0.0.14 — working memory and compaction.
