# ai.yuzu.internal

> v0.0.13 🍊 The agent's internal modules (all AI): consciousness, subconscious, planning/cognition, memory, learning.

| Sub-package | Responsibility |
|---|---|
| `consciousness` | The pool, the single-threaded main loop, pool rendering and persistence |
| `subconscious` | Bounded subconscious threads supervising the main consciousness |
| `memory` | Working memory (habit and deep memory added later) |

More sub-packages (intake, planning, cognition, memory) are added in later steps.
