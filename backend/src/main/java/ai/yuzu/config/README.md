# ai.yuzu.config

> v0.0.2 🍊 Typed configuration and core infrastructure beans.

- `YuzuProperties` — the `yuzu.*` namespace: workgroup `zone`, `workspace-root` (string, resolved with
  `workspaceRootPath()` against the working directory), `cors-origins`.
- `CoreConfig` — `Clock`, `NaturalTime`, the virtual-thread `ExecutorService`, the single timer
  thread, and the guarded `AsyncRunner`.
- `WebConfig` — CORS for direct frontend access to `/api/**`.
