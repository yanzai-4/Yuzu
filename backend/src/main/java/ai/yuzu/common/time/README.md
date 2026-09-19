# ai.yuzu.common.time

> v0.0.1 🍊 Natural-language time for agents and humans; UTC for the database.

- `NaturalTime` — renders instants as `Saturday, September 19, 2026 at 11:32:05 AM PDT` (full) or
  `Sat Sep 19, 11:32:05 AM` (compact) in the workgroup zone; parses the canonical
  `yyyy-MM-dd HH:mm:ss` form that AI outputs use for time ranges. Backed by an injectable `Clock`.
- `DbTime` — converts `Instant` ⇄ UTC `LocalDateTime` for `DATETIME(3)` columns.

Rule: never show epoch numbers to an agent or a human.
