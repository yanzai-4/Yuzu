# ai.yuzu.workspace

> v0.0.11 🍊 Per-agent workspaces: large storage that an agent can never step outside of.

Every agent owns `<yuzu.workspace-root>/agent-xxxx/`. The database only stores previews and paths;
large artifacts (downloaded pages, code, tool results over 16 KB, raw LLM payloads, deep-memory
bodies) live here.

```
agent-xxxx/
  files/          agent-written documents            (writable, metered)
  code/           code written/run by the sandbox    (writable, metered)
  web/            pages saved by the web tool        (writable, metered)
  memory/         deep-memory bodies                 (writable, metered)
  tool-outputs/   large tool results, by day         (platform-only, metered)
  llm/            raw LLM payloads, by day           (platform-only, NOT metered)
```

## Main classes

- `WorkspaceService` — `forAgent(AgentId)` returns the agent's `AgentWorkspace`; the folder tree is
  created lazily on first use (and recreated if it disappears).
- `AgentWorkspace` — every operation, all addressed by **relative** paths with `/` separators:
  `list`, `stat`, `exists`, `readText`, `readChunk`, `readLines`, `countLines`, `writeText`,
  `append`, `delete`, `saveToolOutput`, `saveToolOutputIfLarge`, `saveLlmPayload`, `usedBytes`,
  `quotaBytes`, `invalidateUsage`, `resolve` (guarded absolute path for platform code).
- `WorkspacePathGuard` (internal) — the sandbox boundary, see below.
- `LargeFileReader` (internal) — streaming readers: byte-level line scanning with a fixed 64 KB
  buffer and UTF-8-boundary-aware chunks. Skipped lines are never decoded, long lines are cut with a
  marker, so a 50 MB+ file (even a single 50 MB line) is read with a few hundred KB of memory.
- `AtomicFiles` (internal) — temp file in the same folder + `fsync` + atomic rename; appends open
  with `O_NOFOLLOW`.
- `WorkspaceUsage` (internal) — cached metered size (file walk that skips `llm/`), invalidated on
  every write and refreshed at least every 60 s.
- `WorkspaceQuota` / `AgentWorkspaceQuota` — quota source: the agent's `Limits.fileQuotaMb` (MiB),
  read on every metered write so limit edits apply immediately.
- Values: `WorkspaceEntry`, `WorkspaceListing`, `TextChunk`, `LineSlice`, `WorkspaceArea`.

## Sandbox rules ("never step outside")

`SANDBOX_VIOLATION` (`SandboxViolationException`, details `agentId`, `path`, `reason`) is thrown for:

| reason | example |
|---|---|
| `parent-escape` | `../agent-beef/files/x`, `files/../../etc` |
| `absolute-path` | `/etc/passwd`, `~/.ssh/id_rsa`, `C:/Windows` |
| `control-character` | NUL, CR/LF, other controls, bidi/format characters, lone surrogates |
| `backslash` | `files\..\x` |
| `symbolic-link` | any existing path component is a link (even one pointing inside), or the OS reports a link loop |
| `outside-root` | the real path of the deepest existing component is not under the real agent root |
| `hard-link` | reading or appending to a file with more than one hard link (it may alias a file outside) |
| `special-file` | FIFOs, sockets and devices (opening them could block forever) |

Other rules: paths are normalized lexically (`./a//b/../c` → `a/c`); names are limited to 512
characters / 255 bytes per segment; files are opened with `NOFOLLOW_LINKS`; parents created for a
write are re-checked afterwards. Error messages never contain absolute server paths.

## Reads

- `readText(path, maxBytes)` / `readChunk(path, offset, length)` — 16 B..4 MB per call, both ends
  moved to UTF-8 character boundaries; continue from `TextChunk.nextOffsetBytes` until `eof`.
  `binary` is set when the chunk contains NUL bytes.
- `readLines(path, from, to)` — 1-based inclusive; at most 5,000 lines / 1 MB of text per call, lines
  longer than 16 KB are cut with a `… [line cut: N MB in total]` marker; `hasMore` says lines follow,
  `truncated` says a budget or a cut line shortened the answer. `LineSlice.numbered()` renders
  `12| text` lines for prompts. `countLines(path)` streams the whole file once.
- `list(dir)` — folders first, then names; at most 1,000 entries (`truncated` + `totalEntries`);
  links and special files are listed with kind `SYMLINK` / `OTHER` but can never be opened.

## Writes, limits and quota

- Generic writes (`writeText`, `append`, `delete`) only target files inside `files/`, `code/`,
  `web/` or `memory/`; `tool-outputs/` and `llm/` are written by the platform only
  (`PERMISSION_DENIED`, reason `read-only-area` / `outside-areas`).
- `writeText` ≤ 1 MB per call and atomic; `append` ≤ 1 MB per call (the file may grow up to the quota).
- Quota: metered bytes (everything except `llm/`) may not exceed `fileQuotaMb` MiB →
  `PermissionDeniedException` with message "Workspace quota exceeded…" and details
  `reason=quota`, `usedBytes`, `quotaBytes`, `requestedBytes`. Shrinking or overwriting with smaller
  content is always allowed, and `delete` frees space.
- `saveToolOutput(name, content)` → `tool-outputs/<yyyy-MM-dd>/<HHmmss>-<name>-<6hex>.txt`
  (metered, ≤ 64 MB). `saveToolOutputIfLarge` does it only above 16 KB
  (`INLINE_RESULT_LIMIT_BYTES`).
- `saveLlmPayload(callId, json)` → `llm/<yyyy-MM-dd>/<callId>.json` (not metered, ≤ 64 MB) so
  telemetry can never eat an agent's quota.
- Code outside this API that writes into a workspace (the code sandbox) calls `invalidateUsage()`.

## Data flow

```
tool layer / LLM layer ─▶ WorkspaceService.forAgent(id) ─▶ AgentWorkspace
   read*  ─▶ guard.normalize → guard.check (links, containment) → regular-file checks → LargeFileReader
   write* ─▶ guard → per-agent ReentrantLock → prepare parent + re-check → quota reserve → AtomicFiles → usage.invalidate
```

Concurrency: one `ReentrantLock` per agent serializes writes (never `synchronized`); reads take no
lock (atomic renames mean readers see either the old or the new file). Residual risk: pure Java has
no `openat`, so a process racing inside the workspace (the future code sandbox) could swap a folder
for a link between the check and the open; the re-checks and `O_NOFOLLOW` keep that window small.
