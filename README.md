# Yuzu 🍊 — AI coworkers that actually get work done

Yuzu is a multi-agent workplace: a Slack-like group chat where humans and up to eight citrus-named AI
agents collaborate. Every agent shares the same internal architecture (chat triage, safety review,
planning/cognition, a single-threaded main consciousness, subconscious supervision, habit and deep
memory, behavior review, tool calling) but has its own role, permissions, workspace and memory.

- **Left pane** — group chat with @mentions, yellow security notices, question and approval cards.
- **Middle pane** — the office floor: each citrus agent at its desk, with a live bubble showing the
  active module and a short summary of what it is doing.
- **Right pane** — tickets and task lists, token usage and cache-hit rate, simulated emails/trades,
  and a full trace of every module event.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the design.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5 (virtual threads), JdbcClient, Flyway, Caffeine |
| Database | MySQL 9.x (InnoDB, FULLTEXT ngram) |
| LLM | OpenAI-compatible Chat Completions (OpenAI or the EdgeOne Makers gateway) |
| Frontend | React 19, TypeScript, Vite, Zustand, Tailwind CSS v4, motion |
| Realtime | Server-Sent Events + REST |

## Run locally

> Detailed instructions are completed as the build progresses (see `scripts/`).

```bash
./scripts/dev.sh
```

## Conventions

- Version starts at `0.0.0` (see `VERSION`): patch bump per step, minor bump per milestone.
- Every class/function carries a one-line comment `v<version> 🍊 <what it does>`.
- Every package/feature folder has a `README.md` describing its responsibility.
- All code, comments, UI text and prompts are in English.
