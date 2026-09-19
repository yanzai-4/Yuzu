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
- **Guided tour** — six numbered chips under the top bar jump the workspace to the state that shows
  off one pillar of the design (triage, model tiering, subconscious, safety, memory, business).
  Keys `1`–`6` select a step, `Esc` leaves. The tour only changes what is on screen: it never posts a
  message or touches the simulated world.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the design, [`docs/API.md`](docs/API.md) for the
REST/SSE contract, and [`docs/DEMO.md`](docs/DEMO.md) for a ten-minute walkthrough.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5 (virtual threads), JdbcClient, Flyway, Caffeine |
| Database | MySQL 9.x (InnoDB, FULLTEXT ngram) |
| LLM | OpenAI-compatible Chat Completions (OpenAI or the EdgeOne Makers gateway) |
| Frontend | React 19, TypeScript, Vite, Zustand, Tailwind CSS v4, motion |
| Realtime | Server-Sent Events + REST |

## Run locally

Prerequisites: JDK 21, Maven 3.9+, Node 20+, MySQL 9.x binaries (`mysqld`, `mysql`) on the PATH.

```bash
./scripts/db.sh init-db   # first run: private MySQL instance on 127.0.0.1:3307 (data in data/mysql)
./scripts/dev.sh          # starts MySQL, the backend (:8080) and the frontend (:5173)
curl -X POST "http://localhost:8080/api/demo/seed?roomId=room-0001"   # hire the four demo coworkers
```

The frontend must stay on port 5173 — it is the only origin the backend's CORS policy accepts.

The project runs its own MySQL instance (`scripts/db.sh start|stop|status|shell`) so it never touches a
system MySQL installation. Backend tests use the `yuzu_test` database on the same instance.

## Conventions

- Version starts at `0.0.0` (see `VERSION`): patch bump per step, minor bump per milestone.
- Every class/function carries a one-line comment `v<version> 🍊 <what it does>`.
- Every package/feature folder has a `README.md` describing its responsibility.
- All code, comments, UI text and prompts are in English.
