# ai.yuzu.tool.impl.code

> v0.0.26 🍊 The code tool: write a file into the workspace, optionally run it offline.

- `CodeWriteTool` (`code_write`, needs `CODE_WRITE`, risk HIGH, 60 s) — arguments `path`, `content`, `run`.
  The file always lands under the agent's `code/` area (`ai.yuzu.workspace` enforces the sandbox boundary,
  so `../` escapes come back as a readable ERROR). `run = true` additionally requires `CODE_EXECUTE`, which
  is re-checked here with `PermissionGuard` (defense in depth) on top of the dispatcher's own check.
- `SandboxRunner` — starts `/usr/bin/sandbox-exec` with the profile from `SandboxProfile`: deny by default,
  **no network at all**, writes only under the agent workspace, reads allowed. Plus a 20 s wall-clock limit,
  a cleaned environment (`PATH`, `HOME`, `TMPDIR`, `LANG` only), no stdin, and stdout/stderr captured on
  virtual threads and truncated at 4,000 characters each.
- **`sandbox-exec` is deprecated on macOS.** If it is missing (or the interpreter is), the file is still
  written but **nothing runs**, and the result says so in plain words. We never fall back to an unsandboxed
  process.
- `ScriptRuntime` — the only interpreters that may start, chosen by extension: `.py`, `.sh`, `.js`. Any other
  file is written and never executed.
- `RunOutcome` — ran / reason, exit code, stdout, stderr, timed out, truncated, duration.
- **Trusted by code** (plan section 8 / optimization 7): the result text is written by our own code plus the
  script's own output, so `ToolSpec.trusted = true` — it skips the AI outbound review but still passes the
  code checks (secret scanning and redaction) in `ai.yuzu.external.safety`.
