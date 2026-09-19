# ai.yuzu.external.behavior

> v0.0.18 🍊 Reviews of what the main consciousness wants to do (before anything runs).

- `BehaviorReviewModule` (DEFAULT tier, template `behavior`) — sees only the actions, the security guideline
  and the permission scope; if ANY action is non-compliant the whole batch is rejected (the main
  consciousness must re-request). Fails closed. Cacheable (same actions + same scope = same verdict).
- `HighRiskReviewModule` (IMPORTANT tier, template `high_risk`) — independent second look at concrete
  high-risk tool calls (e-mail, trading, code execution); can reject or require a human's confirmation.
- `BehaviorVerdict`, `HighRiskVerdict`.
