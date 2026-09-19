# ai.yuzu.module

> v0.0.18 🍊 The base classes every AI module is built on.

- `AiModule<I,O>` — template method: monitor span → prompt (S0 handbook + S1 module template + schema text
  when the provider cannot enforce it, then the subclass's S2–S7, then the current time captured once per
  invocation) → `LlmGateway.structured` (tier gating, metering, 3 format retries, semantic checks) →
  graceful `degrade` fallback → errors surfaced to the UI. Subclasses implement `spec`, `compose` and
  optionally `semanticErrors`, `degrade`, `startText`, `endText`, `staticInstructions` (static S1 extras).
- `TextAiModule<I>` — the same for free text, optionally streamed.
- `ModuleSpec` — module name, label, tier, template, output record, cacheable flag.
- `ModuleReporter` / `ModuleSpan` — reporting boundary to the monitor (logging fallback until the monitor
  adapter is registered).
- `ModuleDeps` — gateway, prompt library, schema factory, reporter, error fan-out, natural time.
- `ContextAssembler` — deterministic renderers for roster (S2), self (S3), working memory (S5), the
  anchored chat window (S6, JSON) and pool batches (S7, JSON), plus first-person chat attributions.

Prompt templates: `backend/src/main/resources/prompts/modules/<template>.md`.
- `TaskStateProvider` — task-list text for prompts (implemented by the task package; `NONE` fallback).
- `ToolCatalogProvider` — permitted-tool and full-tool catalogs for prompts (implemented by the tool registry).
