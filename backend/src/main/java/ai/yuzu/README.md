# ai.yuzu

> v0.0.18 🍊 Root package of the Yuzu backend.

`YuzuApplication` boots Spring Boot with virtual threads enabled. Every sub-package is a feature
module with its own README:

| Package | Responsibility |
|---|---|
| `common` | Cross-cutting building blocks: ids, natural time, errors, concurrency helpers |
| `config` | Typed configuration and core infrastructure beans |
| `persistence` | Agent-scoped repository base class and batched telemetry writer |
| `realtime` | SSE hub, replay buffer, stream endpoint, realtime error reporter |
| `room` | Rooms, human membership (join by username), cached room directory |
| `chat` | Group chat write path, mentions, in-memory anchored window, agent fan-out hook |
| `card` | Question and approval cards: open as fanout-free chat messages, answered over REST, first answer wins, handlers by purpose |
| `task` | Tickets and per-agent task lists (tick/strike only, publisher approval before archive, last 3 archived for planning), REST + snapshot |
| `bootstrap` | `/api/bootstrap` snapshot assembled from feature contributors |
| `agent` | Agents as employees: roles, permissions, limits, citrus identities, hiring (max 8) |
| `agent.runtime` | Per-agent runtime containers keyed by agent id |
| `monitor` | Module monitoring and live agent status |
| `settings` | Console model settings and AES-GCM encrypted API keys |
| `llm` | Model access layer (tiers, provider client, structured output, metering) |
| `module` | AI module base classes (template method), context assembler, monitor reporting boundary |
| `external` | External agent modules: chat triage, safety review, behavior review, tool calling |
| `tool` | Tool SPI, registry, permission guard and concrete tools |
| `internal` | Internal agent modules: consciousness pool/main loop, subconscious, memory, ... |

More packages are added milestone by milestone (see `docs/ARCHITECTURE.md`).
