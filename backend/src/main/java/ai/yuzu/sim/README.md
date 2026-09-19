# ai.yuzu.sim

> v0.0.11 🍊 The simulated world: fake e-mail and fake trading, clearly marked SIMULATED in the UI.

Yuzu is a demo, so agents never touch a real mail server or a real broker. Sending e-mail and trading
are the two high-risk actions; their guard rules are pure functions (`EmailRules`, `TradeRules`) that
the tool layer's permission checks call, and the simulated services enforce the hard limits once more
themselves (defense in depth).

| Sub-package | Responsibility |
|---|---|
| `sim.email` | Per-agent mailbox: seeded inboxes, idempotent sends, BLOCKED records, `EmailRules` |
| `sim.market` | Deterministic quotes (`FakeBroker`), per-agent portfolios and trades (`FakePortfolio`), `TradeRules` |

## Classes in this package

- `SimFeed` — read side for the UI: newest-first e-mails and trades of a room's present agents (or of
  one agent) merged across agents, and portfolios of the room's trading agents (agents with
  `TRADE_VIEW`/`TRADE_EXECUTE` get a virtual $10,000 account until they trade; anyone with a stored
  account is always listed). Reads never write (no inbox seeding, no portfolio rows).
- `SimController` — `GET /api/sim/emails`, `GET /api/sim/trades`, `GET /api/sim/portfolios`
  (query: `roomId` default `room-0001`, optional `agentId`, `limit` default 100, max 500). DTOs match
  `frontend/src/api/types.ts` (`Email`, `Trade`, `Portfolio`) exactly; times are
  `NaturalTime.compact` strings, money and quantities are JSON numbers, lists are newest first.
- `SimSnapshotContributor` — adds `emails` and `trades` (latest 50 each) and `portfolios` of the
  room's present agents to `/api/bootstrap`.

## Data flow

```
tool layer ─▶ EmailRules.check / TradeRules.decide (pure)      ─▶ route: send / execute / approval card / block
          ─▶ FakeMailbox.send ─┐                                  (hard rules checked again inside)
          ─▶ FakePortfolio.executeTrade / recordTrade / approvePending / rejectPending
                               └─▶ MySQL (fake_email, fake_trade, fake_portfolio) ─▶ SseHub: sim.email / sim.trade / sim.portfolio
UI ─▶ /api/bootstrap (SimSnapshotContributor) + /api/sim/* (SimController) ─▶ SimFeed
```
