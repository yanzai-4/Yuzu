# ai.yuzu.llm.usage

> v0.0.11 🍊 Token usage normalization and metering.

- `UsageNormalizer` — OpenAI (`prompt_tokens_details.cached_tokens`), Responses-style (`input_tokens`),
  DeepSeek (`prompt_cache_hit_tokens`), Kimi (`cached_tokens`) → one `Usage`.
- `Usage` — prompt, cached, cache-write, completion, reasoning tokens; `cacheReported` tells whether the
  call may count toward the cache hit rate.
- `TokenMeter` — lock-free LongAdder counters by (agent, module, tier, model): calls, attempts, prompt,
  cached, cache-write, completion, reasoning, latency, errors, retries; hit rate = Σcached ÷ Σprompt over
  calls that reported caching; local response-cache hits/lookups. Publishes `usage.tick` at most every 2 s
  when something changed.
- `UsageSnapshot` / `UsageRow` — contract types; `UsageSnapshotContributor` adds them to `/api/bootstrap`.
