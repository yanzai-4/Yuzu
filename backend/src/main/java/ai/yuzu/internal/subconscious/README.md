# ai.yuzu.internal.subconscious

> v0.0.12 🍊 Subconscious threads (the module itself arrives in a later step).

- `SubconsciousScheduler` — one pass per new non-subconscious pool message, at most 2 concurrent per agent;
  while saturated, new messages coalesce into one waiting batch (never dropped). Each pass only sees its
  own new messages (current round only).
- `SubconsciousHandler` — implemented by the subconscious module.
