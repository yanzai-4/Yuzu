# ai.yuzu

> v0.0.3 🍊 Root package of the Yuzu backend.

`YuzuApplication` boots Spring Boot with virtual threads enabled. Every sub-package is a feature
module with its own README:

| Package | Responsibility |
|---|---|
| `common` | Cross-cutting building blocks: ids, natural time, errors, concurrency helpers |
| `config` | Typed configuration and core infrastructure beans |
| `persistence` | Agent-scoped repository base class and batched telemetry writer |
| `realtime` | SSE hub, replay buffer, stream endpoint, realtime error reporter |

More packages are added milestone by milestone (see `docs/ARCHITECTURE.md`).
