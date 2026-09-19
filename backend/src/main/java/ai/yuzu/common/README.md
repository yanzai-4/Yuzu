# ai.yuzu.common

> v0.0.1 🍊 Dependency-free building blocks shared by every feature package.

| Sub-package | Responsibility |
|---|---|
| `id` | Agent / user / room / record identifiers (`agent-xxxx`, `<name>-<agentHex>-<10hex>`) |
| `time` | Natural-language time rendering and UTC database conversion |
| `error` | `ErrorCode`, the `YuzuException` hierarchy, `ApiError`, the REST exception handler |
| `concurrent` | Guarded async execution, error sinks, cooperative cancellation |
| `json` | JSON column helpers and canonical rendering |

Rule: nothing in `common` may depend on a feature package.
