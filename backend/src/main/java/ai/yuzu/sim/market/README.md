# ai.yuzu.sim.market

> v0.0.11 🍊 Simulated market data, portfolios and trades (tables `fake_trade`, `fake_portfolio`;
> events `sim.trade`, `sim.portfolio`).

## Market data

- `Instrument` — CITR (Citrus Holdings), LIME (Lime Logistics), YUZU (Yuzu Labs), PEEL (Peel
  Packaging), ZEST (Zest Foods), each with a base price and a per-minute volatility.
  `Instrument.require(symbol)` → NOT_FOUND listing the available symbols.
- `PriceModel` — price = f(instrument, epoch minute): 11 layers of seeded value noise, layer k
  changing every 2^k minutes with amplitude `volatility × √(2^k)` (random-walk scaling). No state, no
  background thread, reproducible, and hard-bounded around the base price (`maxLogMove`).
- `FakeBroker` — `quote(symbol)` / `quotes()` at the current minute (clock from `NaturalTime`), with
  the change since local midnight. `Quote.notional(qty)` = qty × price rounded to cents.

## Trading

- `TradeRules` (pure) — `decide(Limits, notionalUsd)`:
  `EXECUTE` if `notional ≤ tradeAutoApproveUsd`, `NEEDS_APPROVAL` if `≤ tradeMaxNotionalUsd`,
  `BLOCKED` above the max, when the max is 0, or for non-positive/NaN notionals — each with an
  English reason.
- `FakePortfolio` — one account per agent (starts with $10,000 cash):
  - `portfolio(agentId)` — stored account or the untouched starting one (reads never write).
  - `executeTrade(agentId, symbol, side, qty)` — at the current price. Hard limits (active agent,
    `TRADE_EXECUTE`, max notional) → BLOCKED row + `PermissionDeniedException`; missing cash
    (no negative cash) or shares (no short selling) → REJECTED row + `ConflictException`; otherwise
    EXECUTED row + portfolio update. The auto-approve threshold is **not** enforced here: routing
    trades above it to an approval card is the tool layer's job (`TradeRules`).
  - `recordTrade(agentId, symbol, side, qty, status, reason, cardId)` — PENDING_APPROVAL, REJECTED or
    BLOCKED rows (EXECUTED is refused), priced at the current quote.
  - `approvePending(tradeId)` — executes a pending trade at the current price; hard limits apply again
    (BLOCKED if the limits shrank meanwhile), missing cash/shares → REJECTED; not pending → CONFLICT.
  - `rejectPending(tradeId[, reason])` — pending → REJECTED.
  - `trade(agentId, tradeId)`, `trades(agentId, limit)`, `findPortfolio(agentId)`.
- `PortfolioLedger` (internal) — the atomic part: portfolio update (optimistic `version`, each retry a
  fresh transaction, 3 attempts) and trade insert/decision in **one** transaction. A pending trade is
  decided with `UPDATE … WHERE status = 'PENDING_APPROVAL'`, so it can never be approved twice.
- `Portfolio` — pure trade math (`rejectionFor`, `apply`: cash, quantities, weighted average price).
- `Trade`, `TradeView`, `Portfolio`, `PortfolioView`, `Order` (validated input: known symbol, side,
  positive quantity with ≤ 4 decimals, ≤ 1,000,000).

## Concurrency and events

Per-agent `ReentrantLock` around quote → limit check → settlement; optimistic `version` protects the
row across processes. Events are published after the transaction commits: `sim.trade` for every trade
row change and `sim.portfolio` whenever cash or positions change, to the agent's room.
