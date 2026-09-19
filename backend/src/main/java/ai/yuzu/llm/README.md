# ai.yuzu.llm

> v0.0.8 🍊 Model access layer (OpenAI-compatible Chat Completions).

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
