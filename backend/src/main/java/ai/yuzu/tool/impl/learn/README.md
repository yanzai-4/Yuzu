# ai.yuzu.tool.impl.learn

> v0.0.28 🍊 Learning on purpose (the subconscious also learns, quietly).

- `LearnTool` (`learn`) — arguments `name`, `scenario`, `technique`. When the main consciousness concludes
  "I need to learn this", the action becomes this call and `internal.memory.LearningModule` applies exactly
  the same flow the subconscious gets: content hash → FULLTEXT search → `MemoryJudge` → merge / overwrite /
  ignore, and a contradiction held for 10 rounds instead of an overwrite. The tool reports back what actually
  happened ("I learned a new habit …", "I already remember this habit …", "This conflicts with a habit I
  already have …"), so the agent does not try again.
  - No permission gates it: remembering how one's own work went is not an outside effect, and an agent that
    could act but not learn from it would repeat every mistake. `PermissionGuard` is still called first, so
    the defense-in-depth contract holds if a permission is ever added.
  - Trusted output (our own code, no outside content), so it skips the AI outbound review.
  - Habits only. One-off facts belong in deep memory, which the subconscious writes; recalling anything is
    `memory_read`.
