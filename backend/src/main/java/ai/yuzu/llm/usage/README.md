# ai.yuzu.llm.usage

> v0.0.31 🍊 Token usage normalization, metering and the automatic budget pause.

- `UsageNormalizer` — OpenAI (`prompt_tokens_details.cached_tokens`), Responses-style (`input_tokens`),
  DeepSeek (`prompt_cache_hit_tokens`), Kimi (`cached_tokens`) → one `Usage`.
- `Usage` — prompt, cached, cache-write, completion, reasoning tokens; `cacheReported` tells whether the
  call may count toward the cache hit rate.
- `TokenMeter` — lock-free LongAdder counters by (agent, module, tier, model): calls, attempts, prompt,
  cached, cache-write, completion, reasoning, latency, errors, retries; hit rate = Σcached ÷ Σprompt over
  calls that reported caching; local response-cache hits/lookups. Publishes `usage.tick` at most every 2 s
  when something changed. `billableTokens()` is the running prompt + completion total the budget reads.
- `BudgetProperties` — `yuzu.llm.budget.{max-total-tokens, max-cost-usd, usd-per-million-tokens}`; both
  ceilings are off by default so a demo never stops itself.
- `TokenBudget` — consulted by `PriorityGate` before every model call. The first call that finds the
  ceiling reached flips the pause under a `ReentrantLock`, publishes ONE platform-wide `error` event
  (`BUDGET_EXHAUSTED`) and from then on refuses every call with `BudgetExhaustedException`, which each
  module turns into its own graceful degradation instead of hammering the provider. `setLimits(...)` and
  `resume()` put the agents back to work.
- `UsageSnapshot` / `UsageRow` — contract types; `UsageSnapshotContributor` adds them (plus `budget`,
  the `TokenBudget.Status`) to `/api/bootstrap`.
