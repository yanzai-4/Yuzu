# ai.yuzu.internal.subconscious

> v0.0.28 🍊 The quiet thread beside the main consciousness: hunches, learning, and unsettled contradictions.

- `SubconsciousScheduler` — one pass per new non-subconscious pool message, at most 2 concurrent per agent;
  while saturated, new messages coalesce into one waiting batch (never dropped). Each pass only sees its
  own new messages (current round only).
- `SubconsciousHandler` — implemented by `SubconsciousService`.
- `SubconsciousModule` — the AI module (DEFAULT tier, template `subconscious`). Sees this round's pool
  messages, working memory (marked "already known, do not learn it again"), the task list, the roster, its
  own profile, the unresolved conflicts and the current time. Returns `SubconsciousOutput`
  (`reasoning, advice?, learn[], remember[], conflictUpdates[]`). When it cannot think it stays silent, by
  design: the subconscious must never disturb the main consciousness with its own failures.
- `SubconsciousService` — settles and ages conflicts, drops the advice into the pool, then hands the
  candidates to `LearningModule` and `MemoryModule` on their own virtual threads (a slow judge must not hold
  the agent's two subconscious permits).
- `ConflictTracker` — per-agent `conflictLock`. One activation = one subconscious pass, and always in this
  order: apply what this round settled (KEEP_OLD / USE_NEW / MERGE), then count one round down on everything
  still open. At zero the conflict is EXPIRED and the old memory stays.

## Why advice cannot wake the main consciousness

Advice enters the pool with `Origin.SUBCONSCIOUS`, and `Origin.triggers()` is false for exactly that value.
`ConsciousnessPool.append` therefore does not raise the trigger count, so `takeAllOrRelease` will not start a
run for it; it rides along in whatever batch the main consciousness takes next. `MainLoop.offer` dispatches a
subconscious pass only for triggers, so advice cannot spawn another pass either. `PoolRenderer` shows SELF and
SUBCONSCIOUS identically ("me (my own thought)"), so the main consciousness reads a hunch as its own thought
and never learns that a subconscious exists.

## Change log

- v0.0.28 — the module, the service, the conflict tracker.
- v0.0.12 — the scheduler skeleton.
