# ai.yuzu.llm.usage

> v0.0.8 🍊 Token usage normalization.

- `UsageNormalizer` — OpenAI (`prompt_tokens_details.cached_tokens`), Responses-style (`input_tokens`),
  DeepSeek (`prompt_cache_hit_tokens`), Kimi (`cached_tokens`) → one `Usage`.
- `Usage` — prompt, cached, cache-write, completion, reasoning tokens; `cacheReported` tells whether the
  call may count toward the cache hit rate.
