# ai.yuzu.llm.prompt

> v0.0.9 🍊 Modular, cache-ordered prompts.

- `SegmentRank` — S0 handbook → S1 module → S2 roster → S3 self → S4 slow state → S5 working memory →
  S6 chat → S7 stimulus → S8 current time. Most repeated content first, the biggest variables last,
  and the time (changes every second) always at the very end.
- `PromptBuilder` — enforces the order (throws on out-of-order segments), renders S0+S1 as the system
  message and S2–S7 as "## Title" sections of the user message, then appends S8. Deterministic output →
  byte-identical prefixes for identical state.
- `Prompt` — rendered messages; `withRetry(invalid, feedback)` appends at the end so retries keep the
  cached prefix.
- `PromptLibrary` — loads `classpath:prompts/**.md` (`handbook`, `modules/<name>`) once; fails fast on a
  missing template.
- `TokenEstimator` — jtokkit `o200k_base` counts and head/tail trimming for context budgets.

Templates live in `backend/src/main/resources/prompts/`. `handbook.md` (company rules, the detailed
security guideline, attribution and time conventions) is > 1,024 tokens so OpenAI prompt caching applies
to every call.
