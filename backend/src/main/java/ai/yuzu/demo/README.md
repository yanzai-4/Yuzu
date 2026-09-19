# ai.yuzu.demo

> v0.0.29 🍊 One-click setup of the collaboration demo: four role-matched coworkers and their simulated world.

## Responsibility

`POST /api/demo/seed?roomId=` prepares a workgroup for the scripted demo
("Launch the Citrus Spark bottle Friday: competitor brief, landing page, email 3 beta customers.") so nobody has
to hire agents by hand. It is **idempotent** and **non-destructive**: a coworker is hired only when its citrus
name is still free in the room, so re-seeding returns exactly the same agents and keeps every edit a human made
(persona, permissions, limits).

| Coworker | Role preset | What the demo needs from it |
|---|---|---|
| Yuzu | `PROJECT_MANAGER` | `TASK_ASSIGN`, `TASK_APPROVE`, `ASK_USER`, `EMAIL_READ`, `WEB_BROWSE` — intake, tickets, approvals |
| Lime | `RESEARCHER` | `WEB_BROWSE`, `FILE_READ`/`FILE_WRITE` — the competitor brief, handed to the engineer |
| Kumquat | `ENGINEER` | `CODE_WRITE`, `CODE_EXECUTE`, files — the landing page; **no** `TASK_ASSIGN`, so `TicketGovernor` applies |
| Pomelo | `CUSTOMER_LIAISON` | `EMAIL_READ`/`EMAIL_SEND` limited to `acme.test` and `example.com` — the beta-customer mails |

Permissions come from `Role.defaultScope()` (the single source of truth); the seeder only pins the liaison's
e-mail allowlist to the domains of the seeded inbox, so the demo mails are deliverable and everything else is
refused by `EmailRules`.

## Classes

- `DemoSeeder` — `seed(roomId)` (per-room `ReentrantLock`, returns the four profiles in demo order) and
  `symbols()` (the simulated tickers). Seeding the world **reuses the bootstrap that already exists in
  `ai.yuzu.sim`**: reading a mailbox seeds its five customer e-mails once (`FakeMailbox`), and quotes are derived
  from the clock (`FakeBroker`), so there is nothing to duplicate and nothing to clean up.
- `DemoController` — `POST /api/demo/seed?roomId=` → `Agent[]` (`roomId` defaults to `room-0001`).

## Collaborators

`ai.yuzu.agent.AgentService` (`createNamed`, the 8-agent limit and citrus names),
`ai.yuzu.sim.email.FakeMailbox`, `ai.yuzu.sim.market.FakeBroker`.

## Changes

- v0.0.29 — created for step S32 (collaboration scenario): `DemoSeeder`, `DemoController`.
