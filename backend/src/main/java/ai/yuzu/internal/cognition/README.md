# ai.yuzu.internal.cognition

> v0.0.28 🍊 Cognition (habit recall): the read side of habit memory.

- `HabitAdvisor` — recalls habits (techniques) that fit a stimulus and returns a first-person note. The AI
  implementation lands with the cognition module; until then intake resolves it optionally and skips it.
- `HabitIndexService` — the habit index cognition puts into its prompt: one `- <id>: <name> — use it <when>`
  line per active habit, rendered deterministically (it is part of a cache-stable prompt prefix) and cached
  per agent in Caffeine. `internal.memory.HabitMemoryService` calls `invalidate(agentId)` inside every write,
  so a habit learned a moment ago is in the index on the very next pass. `isEmpty(agentId)` is how cognition
  knows to skip its model call entirely for an agent that has learned nothing yet.

## Change log

- v0.0.28 — the habit index and its write-through invalidation.
- v0.0.16 — the `HabitAdvisor` contract.
