# ai.yuzu.web

> v0.0.26 🍊 The only door to the outside web: search, fetch, extract, guard and snapshot.

Everything this package returns is **untrusted outside content**. It reaches an agent only through the
`web_browse` tool (`ai.yuzu.tool.impl.web`), which is declared `trusted = false`, so the outbound safety
review (`ai.yuzu.external.safety`, MASK mode) always inspects it before the agent reads it.

## Modes

`yuzu.web.mode` picks where content comes from:

| Mode | Fetcher | Use |
|---|---|---|
| `LIVE` (default) | `LiveWebFetcher` | DuckDuckGo's HTML endpoint for search, direct GETs for pages |
| `CORPUS` | `CorpusWebFetcher` | `WebCorpus`: five fixed offline pages, one of them a prompt-injection page |

Both go through the same extraction path, so a demo on the corpus behaves like the real thing minus the
network. Tests only ever use `CORPUS` (or parse fixed HTML), never the live internet.

## Main classes

- `WebService` — entry point: mode selection, size limits, and a snapshot of every opened page written into
  the agent's `web/<date>/<slug>-<time>.txt` area through `ai.yuzu.workspace.WorkspaceService`. A snapshot
  that cannot be written is logged and skipped; it never fails the browse.
- `SsrfGuard` — the security boundary. `checkSyntax` (no name server): http/https only, no credentials in
  the URL, ports 80/443 only, and literal IP addresses checked. `check` additionally resolves the host and
  refuses when **any** returned address is loopback, private, link-local (including the cloud metadata
  address `169.254.169.254`), unique-local, CGNAT, benchmarking, multicast, broadcast or an IPv4-mapped IPv6
  address. Every redirect hop is checked again. Refusals are `SANDBOX_VIOLATION`.
- `LiveWebFetcher` — `java.net.http` with redirects turned off and followed by hand, one wall-clock deadline
  for the whole call (`yuzu.web.timeout`), a hard body cap (`yuzu.web.max-bytes`), a content-type check and
  charset decoding.
- `CorpusWebFetcher` / `WebCorpus` — deterministic keyword search and fixed HTML pages; `WebCorpus.injection()`
  is the page that tells the reader to ignore its instructions and leak secrets (the masking demo).
- `HtmlExtractor` — Jsoup: title, block-level readable text (scripts, styles, navigation and footers removed),
  up to ten absolute links, cut to `yuzu.web.max-page-chars`.
- `DuckDuckGoResults` — parses the HTML endpoint into `SearchHit`s and unwraps its `/l/?uddg=` redirect links;
  ads are skipped.
- Records: `WebPage`, `WebLink`, `SearchHit`; settings `WebProperties` (`yuzu.web.*`) and `WebMode`.

## Known limitation

The JDK resolves the host again when it connects, so a hostile name server could in theory answer differently
between our check and the connection (DNS rebinding). The window is very small and nothing private is
reachable from the process, but we do not claim to close it.

## Changes

- v0.0.26 — new package (S26).
