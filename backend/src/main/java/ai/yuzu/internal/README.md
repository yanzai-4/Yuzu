# ai.yuzu.internal

> v0.0.28 🍊 The agent's internal modules (all AI): consciousness, subconscious, planning/cognition, memory, learning.

| Sub-package | Responsibility |
|---|---|
| `consciousness` | The pool, the single-threaded main loop, pool rendering and persistence |
| `subconscious` | Bounded subconscious threads: hunches, learn/remember candidates, the 10-round conflict tracker |
| `memory` | Working memory, plus habit and deep memory behind one write flow (hash → search → judge) |
| `intake` | Single entry path into the mind (safety → planning ∥ cognition → pool) |
| `planning` | Task-list planning (`TaskPlanner`) |
| `cognition` | Habit recall (`HabitAdvisor`) and the cached habit index (`HabitIndexService`) |
