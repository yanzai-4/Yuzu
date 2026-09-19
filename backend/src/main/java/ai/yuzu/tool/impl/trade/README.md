# ai.yuzu.tool.impl.trade

> v0.0.27 🍊 Trading tools over the simulated market: free to look, limited to act, approved to act big.

- `MarketQuoteTool` (`market_quote`, needs `TRADE_VIEW`, LOW) — argument `symbol?`. One `FakeBroker` quote
  (price and change since the open) or the whole board (CITR, LIME, YUZU, PEEL, ZEST); an unknown symbol is
  NOT_FOUND with the list of valid ones. Trusted output (our own market data).
- `PortfolioReadTool` (`portfolio_read`, needs `TRADE_VIEW`, LOW) — argument `recentTrades` (0–20). Cash,
  each position with its average and current price and value, **the agent's own limits in words** (maximum
  per trade and auto-approve threshold, so it never plans a trade it may not place), and optionally the
  latest trades with their status and reason. Reads never write: an agent that never traded sees the
  untouched $10,000 starting account. Trusted output.
- `TradeExecuteTool` (`trade_execute`, needs `TRADE_EXECUTE`, **HIGH**) — arguments `symbol`, `side`
  (BUY / SELL), `quantity`. Calls `FakePortfolio.executeTrade` and does nothing else with the money:
  - within `tradeAutoApproveUsd` → EXECUTED, returned as `OK` with the fill and the trade id;
  - above it (but within `tradeMaxNotionalUsd`) → the broker parks a PENDING_APPROVAL trade, the tool opens
    an Approve / Reject card (`TRADE_APPROVAL`, payload `PendingTrade`) and returns `WAITING`;
  - above `tradeMaxNotionalUsd`, without `TRADE_EXECUTE` or for a retired agent → the broker writes a
    BLOCKED row and throws `PERMISSION_DENIED`; **no card is opened**, because no human may lift that limit;
  - no cash or no shares (no negative cash, no short selling) → REJECTED row and `CONFLICT`.
- `TradeApprovalHandler` (`TRADE_APPROVAL`) — approved → `approvePending` settles at the price of that
  moment and re-checks the hard limits (still BLOCKED if the limits shrank meanwhile); rejected →
  `rejectPending`. The ledger decides a pending row with `UPDATE … WHERE status = 'PENDING_APPROVAL'`, so a
  trade can never be approved twice. The outcome reaches the agent as a `NoticeService` notice attributed to
  the deciding human.
- `TradeFormat` — English money, quantities, quotes and order lines. `PendingTrade` — the card payload.

`PermissionGuard` runs first in every `execute`, so a direct call that bypasses `ToolDispatcher` still fails.
No limit, threshold or approval rule is duplicated here: `TradeRules`, `FakePortfolio` and `PortfolioLedger`
remain the only authorities.
