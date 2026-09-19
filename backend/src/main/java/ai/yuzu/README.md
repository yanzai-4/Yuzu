# ai.yuzu

> v0.0.1 🍊 Root package of the Yuzu backend.

`YuzuApplication` boots Spring Boot with virtual threads enabled. Every sub-package is a feature
module with its own README:

| Package | Responsibility |
|---|---|
| `common` | Cross-cutting building blocks: ids, natural time, errors, concurrency helpers |
| `config` | Typed configuration and core infrastructure beans |

More packages are added milestone by milestone (see `docs/ARCHITECTURE.md`).
