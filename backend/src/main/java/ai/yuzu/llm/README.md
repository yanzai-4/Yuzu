# ai.yuzu.llm

> v0.0.11 🍊 Model access layer (OpenAI-compatible Chat Completions).

- `LlmGateway` — THE entry point for modules: tier → model and endpoint from the console settings,
  `PriorityGate` permit, structured call (validation + 3 retries) or free text (optional streaming),
  metering per agent/module/tier/model, attempt recording, 429 back-pressure, optional 10-minute local
  response cache for pure-function calls (hits metered separately).
- `LlmCallContext` — agent, module, tier, trace id, cancel token; `promptCacheKey()` = `yuzu:{module}:{agent}`.
- `PriorityGate` — global permits per class (MAIN_TOOL 8, CHAT 8, REVIEW 8, BACKGROUND 4, MONITOR 2);
  BACKGROUND halves for 30 s after a 429.
- `LlmCallRecorder` — batched `llm_call` rows per HTTP attempt + request/response JSON payload files in
  the agent workspace (`llm/<date>/<id>.json`; never contains the key).
- `CapabilityProbe` + `LlmController` — `POST /api/settings/llm/test` (per-tier strategy/latency),
  `GET /api/usage`.
- `ModelTier` — IMPORTANT (main consciousness), DEFAULT (most modules and tools), LIGHT (monitor).
- `LlmExecutor` — one logical call: shapes the request by learned capabilities, learns from rejected
  parameters (up to 5 free "learn and resend" steps), retries transport failures (429/5xx/timeouts/I/O)
  3 times with 1/2/4 s jittered backoff honoring Retry-After. A rejected JSON format downgrades the
  strategy and surfaces as `StrategyDowngradedException` so the caller rebuilds its prompt.
- `LlmCall` — messages + tier settings + optional schema, cache key, stream flag, temperature, extra.
- `AttemptObserver` — notified per HTTP attempt (metering and payload recording hook).

Sub-packages: `provider` (HTTP client and wire types), `capability` (what each model accepts),
`usage` (token usage normalization), `structured` (JSON strategies and validation), `prompt`
(modular, cache-ordered prompts).
