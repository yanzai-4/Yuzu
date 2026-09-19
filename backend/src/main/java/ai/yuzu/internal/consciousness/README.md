# ai.yuzu.internal.consciousness

> v0.0.12 🍊 The main consciousness's pool and loop (exactly one run at a time, no lost wake-ups).

- `ConsciousnessPool` — queue + trigger count + `running`/`paused` under one short `ReentrantLock`.
  `append` returns true when the caller must start the loop; `takeAllOrRelease` hands over the WHOLE pool
  or atomically releases ownership when nothing can trigger a run (a pool with only SUBCONSCIOUS
  messages never starts a run; those messages ride along with the next batch).
- `MainLoop` — one virtual thread drains batches through the `MainRunHandler`; a THINK step just appends a
  SELF message; a crashed run restarts only when triggers wait; every non-subconscious message schedules a
  subconscious pass.
- `Consciousness` — per-agent aggregate (pool + loop + subconscious scheduler); `offer(...)` is the ONLY way
  into the pool: persist → append → subconscious → wake.
- `ConsciousnessFactory` — builds it per agent; module handlers are resolved lazily (no construction cycles).
- `Origin` — EXTERNAL / SELF / SUBCONSCIOUS / REVIEW, assigned by code only.
- `PoolMessage`, `PoolMessageRepository` (`pool_message`, agent-scoped), `MainRunHandler`.
- `PoolRenderer` — JSON array `[{from, text}]`; `from` chosen by code (SELF and SUBCONSCIOUS both read
  "me (my own thought)"), text JSON-escaped so external content cannot forge an own-thought entry.

Verified by `ConsciousnessConcurrencyTest` (32 threads × 1,000 messages).
