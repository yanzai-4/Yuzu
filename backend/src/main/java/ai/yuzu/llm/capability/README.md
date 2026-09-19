# ai.yuzu.llm.capability

> v0.0.8 🍊 What each (base URL, model) accepts.

- `CapabilityRegistry` — seeded rules (reasoning models `o*`/`gpt-5*`: no temperature,
  `max_completion_tokens`, reasoning effort; `prompt_cache_key` only on api.openai.com), then learned from
  HTTP 400s (temperature, max tokens field, reasoning effort, stream options, cache key, response format).
  Lessons persist in `app_setting` (`llm.capabilities`). Tracks silent strict-schema failures and downgrades
  after 3 in a row.
- `ModelCapabilities` — immutable record with `with*` copies.
