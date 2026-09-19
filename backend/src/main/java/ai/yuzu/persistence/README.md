# ai.yuzu.persistence

> v0.0.2 🍊 Shared persistence building blocks (MySQL via Spring JDBC).

- `AgentScopedRepository` — base class of every repository that stores agent-internal data.
  `scoped(sql, agentId)` refuses SQL that does not bind `:agentId`; `insertWithFreshId(...)` generates
  `<name>-<agentHex>-<10hex>` ids and retries on the rare duplicate. A unit test
  (`AgentScopedSqlGuardTest`) scans every `SQL_*` constant of subclasses for `agent_id`.
- `BatchWriter<T>` — non-blocking, bounded, batched INSERT writer for telemetry (LLM calls, module
  events). Flushes every 200 ms or per 200 rows via `JdbcTemplate.batchUpdate`
  (`rewriteBatchedStatements=true` turns it into multi-row INSERTs); drops and counts rows when full.
- `BatchWriterFactory` — creates writers sharing the JDBC template, timer and async runner; flushes
  all writers on shutdown.

## Schema

`resources/db/migration/V1__init.sql` creates every table. Agent-scoped tables use
`PRIMARY KEY (agent_id, seq)` (clustered per agent) + `KEY(seq)` + `UNIQUE(id)`; room-scoped tables use
`PRIMARY KEY (room_id, seq)`. FULLTEXT indexes use the ngram parser with stopwords disabled.
