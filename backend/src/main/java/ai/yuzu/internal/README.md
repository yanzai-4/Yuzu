# ai.yuzu.internal

> v0.0.16 🍊 The agent's internal modules (all AI): consciousness, subconscious, planning/cognition, memory, learning.

| Sub-package | Responsibility |
|---|---|
| `consciousness` | The pool, the single-threaded main loop, pool rendering and persistence |
| `subconscious` | Bounded subconscious threads supervising the main consciousness |
| `memory` | Working memory (habit and deep memory added later) |
| `intake` | Single entry path into the mind (safety → planning ∥ cognition → pool) |
| `planning` | Task-list planning (`TaskPlanner`) |
| `cognition` | Habit recall (`HabitAdvisor`) |

More sub-packages (intake, planning, cognition, memory) are added in later steps.
