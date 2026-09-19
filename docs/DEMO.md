# Yuzu 🍊 — demo script

A ten-minute walkthrough of the whole system. Everything below was exercised against a locally running
stack (`scripts/dev.sh`), not a mock.

## 0. Start the stack

```bash
./scripts/db.sh init-db   # first run only: private MySQL on 127.0.0.1:3307
./scripts/dev.sh          # MySQL + backend :8080 + frontend :5173
```

Open <http://localhost:5173> and join with any username — no password, the name is how coworkers
@mention you. The header dot turns green once the SSE stream is live.

> The frontend dev server must be on port 5173: that is the only origin the backend's CORS policy
> accepts. If Vite reports "port in use" and falls back to 5174, every API call answers `403`.

## 1. Seed the office

```bash
curl -X POST "http://localhost:8080/api/demo/seed?roomId=room-0001"
```

This hires four coworkers with role-matched permissions and seeds their simulated world:

| Coworker | Role | Can |
|---|---|---|
| Yuzu | Project Manager | intake, tickets, assignment, approval |
| Lime | Researcher | web browsing, notes |
| Kumquat | Software Engineer | workspace files, writing and running code |
| Pomelo | Customer Liaison | reading the mailbox, sending e-mail (behind an approval card) |

Seeding is idempotent: running it twice changes nothing and never overwrites human edits.

## 2. Give the team a real task

In the chat box:

```
@Yuzu Launch the Citrus Spark bottle Friday: competitor brief, landing page, email 3 beta customers.
```

What to watch, in order:

1. **Chat** — Yuzu acknowledges within a few seconds (the ack fires before planning finishes), then
   creates a ticket per work item and assigns each with an @mention.
2. **Office floor** — each desk animates as its owner moves through modules; the bubble shows the
   current module plus a one-line summary.
3. **Insights → Tasks** — ticket and task-list state stream in over SSE. A task list can only be
   ticked or struck through; archiving requires the publisher's approval, and an unapproved archive
   returns `409`.
4. **Insights → Usage** — token counts per tier and the cache-hit rate; repeat traffic shows
   `cached > 0`.
5. **Insights → Trace** — every module event with its span, in order.

## 3. The four set pieces

**Question card.** When a coworker needs a human decision (for example which tagline to use) it opens
a card in the chat. Answering it goes straight back to the asker, bypassing every chat module, and the
answer is not broadcast to the other agents.

**Injection defence.** The seeded mailbox contains a phishing e-mail ("URGENT: verify your account
credentials"), and the deterministic web corpus contains a prompt-injection page. Untrusted content is
masked on the way out and the room gets a yellow notice instead; nothing reaches the consciousness
pool. Trusted, code-generated results skip the AI review but still get the code check.

**Money needs a human.** Ask for a trade above the auto-approve limit: the trade is recorded as
`PENDING_APPROVAL` and an approval card appears — code, not the prompt, enforces this. Above the hard
per-trade maximum the trade is `BLOCKED` with `PERMISSION_DENIED` and no card is opened at all.
Sending customer e-mail works the same way.

**Recall.** Ask "what happened 5 minutes ago?". Code parses the time phrase first; only if that fails
does the model convert it against the current time, and code validates the result. The recall covers
deep memory and the originals of compacted working memory.

## 4. Safety boundaries worth showing

- `../` in a file path answers `SANDBOX_VIOLATION`; each agent only ever sees its own workspace.
- A generated script that tries to open a network connection fails: `sandbox-exec` denies network by
  default. Where `sandbox-exec` is unavailable the file is written and the result says explicitly that
  it was not run.
- One agent cannot read another's data: every repository is agent-scoped, enforced in SQL.

## 5. Reset

```bash
./scripts/db.sh stop
```

The private MySQL instance lives in `data/mysql` and is never shared with a system MySQL install.
