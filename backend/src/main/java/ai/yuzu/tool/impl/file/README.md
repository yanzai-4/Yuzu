# ai.yuzu.tool.impl.file

> v0.0.27 🍊 Workspace file tools: an agent reads and writes only inside its own sandbox.

- `FileWriteTool` (`file_write`, needs `FILE_WRITE`, MEDIUM) — arguments `path`, `content`, `append`.
  Creates, replaces or appends a text file in `files/`, `code/`, `web/` or `memory/`. Atomic replace,
  1 MB per call and the workspace quota are enforced by `AgentWorkspace`, not here; `tool-outputs/` and
  `llm/` stay platform-only. Trusted output ("Wrote 1.2 KB to files/notes.md …", quota left).
- `FileReadTool` (`file_read`, needs `FILE_READ`, LOW) — arguments `path`, `fromLine?`, `toLine?`,
  `offsetBytes?`. Two continuable modes, both streamed by `LargeFileReader`, so a 50 MB file needs a few
  hundred KB of memory:
  - byte chunks (no line numbers): 16 KB at a time, cut only at UTF-8 character boundaries; the result ends
    with `offsetBytes = …` for the next call, or says the file ended. Binary-looking files are flagged.
  - line ranges: `fromLine`(+`toLine`, default 200 lines) come back numbered (`3998| …`) with the next
    `fromLine` to continue from.
  Untrusted: file content can repeat outside text, so the output passes the outbound safety review.
- `FileListTool` (`file_list`, needs `FILE_READ`, LOW) — argument `path?` (null lists the workspace root).
  Lists a folder (folders first, then names, sizes and natural-language change times) or describes one file;
  links and special files are listed but marked as unopenable. Every result ends with the quota used.
  Untrusted, like `file_read`.
- `FileFormat` — shared English sizes ("1.5 MB"), the 24,000-character output budget and body cutting.

Every tool re-checks `FILE_READ` / `FILE_WRITE` through `PermissionGuard` at the start of `execute`, so a
direct call that bypasses `ToolDispatcher` still fails. Paths are never touched here: `WorkspacePathGuard`
rejects `..`, absolute paths, backslashes, control characters, symlinks and hard links, and its
`SANDBOX_VIOLATION` (`SandboxViolationException`, details `agentId`, `path`, `reason`) is deliberately not
caught, so the agent and the UI see the real sandbox error.
