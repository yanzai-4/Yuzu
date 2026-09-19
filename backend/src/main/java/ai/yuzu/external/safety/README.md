# ai.yuzu.external.safety

> v0.0.16 🍊 Safety review: inbound gate and outbound masking. Fails closed.

- `SafetyService` — facade used by the pipelines.
  - `gate(ctx, content, source)`: a code-level secret scan blocks credentials instantly (no model call);
    otherwise `SafetyGateModule` decides. Blocks become incidents; the intake posts a yellow notice.
  - `mask(ctx, content, source, trusted)`: credentials are always redacted by code; trusted (code-only)
    results skip the AI review (confirmed optimization); otherwise `SafetyMaskModule` returns exact
    passages to mask and `ContentMasker` replaces only those. Masks become incidents (frontend log).
- `SafetyGateModule` / `SafetyMaskModule` (DEFAULT tier, cacheable: same content + same scope = same
  verdict, shared by agents with the same role) on top of `SafetyReviewModule`: sees only the content, the
  security guideline (handbook) and the permission scope. Semantic checks: UNSAFE needs violations; gate
  mode has no masks and needs a user-facing reason; mask quotes must occur verbatim. Degrades to
  "unavailable" (blocked / withheld).
- `SecurityIncidentService` — `security_incident` rows + `security.incident` events (`IncidentView`).
- `SafetyVerdict`, `ContentMasker`.

Templates: `prompts/modules/safety_gate.md`, `prompts/modules/safety_mask.md`.
