# ai.yuzu.tool.impl.web

> v0.0.26 🍊 The web tool.

- `WebBrowseTool` (`web_browse`, needs `WEB_BROWSE`, risk MEDIUM, 60 s) — arguments `query` **or** `url`
  (exactly one). A query returns numbered hits (title, URL, snippet); a URL returns the page title, its
  readable text and its links, and says where the snapshot was saved in the agent's `web/` area.
  All the work happens in `ai.yuzu.web` (`WebService`, `SsrfGuard`, `LiveWebFetcher` / `CorpusWebFetcher`).
- **Untrusted on purpose**: `ToolSpec.trusted = false`, so every result passes the outbound safety review
  (MASK) before the agent reads it. Prompt-injection text on a page is therefore masked, not obeyed; the
  offline corpus contains one such page for the demo.
- Permissions are re-checked with `PermissionGuard` at the start of `execute` (defense in depth), and refused
  addresses (`SANDBOX_VIOLATION`) or failed downloads come back as readable ERROR results, never as crashes.
