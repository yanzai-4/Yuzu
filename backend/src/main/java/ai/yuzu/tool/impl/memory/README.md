# ai.yuzu.tool.impl.memory

> v0.0.19 🍊 The memory-read module (记忆读取模块): on-demand recall of deep memories.

- `MemoryReadTool` (`memory_read`, needs `MEMORY_RECALL`) — arguments `query`, `timePhrase`, `keywords`.
  1. Code resolves the time phrase with `common.time.TimeRangeParser` ("5 minutes ago", "yesterday
     afternoon", "past 2 hours", "last week", "around 11:30 am" and similar).
  2. Only when code cannot resolve a time expression, or there is nothing to search by, the
     `MemoryReadModule` AI (DEFAULT tier) turns the request into a `RecallPlan`, with the current time
     as the last prompt segment. Code checks its range again: both ends set or neither, correct
     format, ordered, not in the future, end clipped to now.
  3. `RecallRepository` searches three stores: deep memories (FULLTEXT ngram and/or time), the
     agent's own working-memory archive (compacted entries included) and the agent's own room chat.
  4. Results come back with natural-language times, capped at 500 characters per item and 8,000 in
     total.
  Untrusted: recalled text can contain outside content, so it passes the outbound safety review.
- `RecallPlan` — `reasoning, keywords[], fromTime?, toTime?` (`yyyy-MM-dd HH:mm:ss`, local time).
