package ai.yuzu.sim.market;

import ai.yuzu.common.error.ConflictException;
import ai.yuzu.common.id.AgentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** v0.0.11 🍊 Atomic settlement: the portfolio (optimistic version, retried) and the trade row change in one transaction. */
@Component
class PortfolioLedger {

    /** v0.0.11 🍊 Attempts before a version conflict is reported (each attempt is a fresh transaction). */
    static final int MAX_ATTEMPTS = 3;

    private final PortfolioRepository portfolios;
    private final TradeRepository trades;
    private final TransactionTemplate tx;

    /** v0.0.11 🍊 Result of a settlement: the trade row and, when cash or positions changed, the new portfolio. */
    record Outcome(Trade trade, Portfolio portfolio) {

        /** v0.0.11 🍊 True when the trade was executed. */
        boolean executed() {
            return trade.status() == Trade.Status.EXECUTED;
        }
    }

    /** v0.0.11 🍊 Injects the repositories and the transaction template. */
    PortfolioLedger(PortfolioRepository portfolios, TradeRepository trades, TransactionTemplate tx) {
        this.portfolios = portfolios;
        this.trades = trades;
        this.tx = tx;
    }

    /** v0.0.11 🍊 Settles a new order: EXECUTED + portfolio update, or a REJECTED row when cash or shares are missing. */
    Outcome execute(AgentId agentId, Order order, BigDecimal price, BigDecimal notional, Instant now) {
        portfolios.ensure(agentId, now);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Outcome outcome = tx.execute(status -> {
                Portfolio current = load(agentId);
                Optional<String> rejection = current.rejectionFor(order.side(), order.symbol(), order.qty(), notional);
                if (rejection.isPresent()) {
                    return new Outcome(trades.insert(Trade.draft(agentId, order, price, notional,
                            Trade.Status.REJECTED, null, rejection.get(), now)), null);
                }
                Portfolio next = current.apply(order.side(), order.symbol(), order.qty(), price, notional, now);
                if (!portfolios.update(next, current.version())) {
                    return null;
                }
                Trade trade = trades.insert(Trade.draft(agentId, order, price, notional, Trade.Status.EXECUTED, null,
                        null, now));
                return new Outcome(trade, next.withVersion(current.version() + 1));
            });
            if (outcome != null) {
                return outcome;
            }
        }
        throw conflict(agentId);
    }

    /** v0.0.11 🍊 Settles an approved pending trade: EXECUTED + portfolio update, or REJECTED if it cannot settle now. */
    Outcome settle(Trade pending, BigDecimal price, BigDecimal notional, String reason, Instant now) {
        AgentId agentId = pending.agentId();
        Order order = pending.order();
        portfolios.ensure(agentId, now);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Outcome outcome = tx.execute(status -> {
                Portfolio current = load(agentId);
                Optional<String> rejection = current.rejectionFor(order.side(), order.symbol(), order.qty(), notional);
                if (rejection.isPresent()) {
                    return new Outcome(decide(pending, Trade.Status.REJECTED, price, notional, rejection.get(), now),
                            null);
                }
                Portfolio next = current.apply(order.side(), order.symbol(), order.qty(), price, notional, now);
                if (!portfolios.update(next, current.version())) {
                    return null;
                }
                Trade executed = decide(pending, Trade.Status.EXECUTED, price, notional, reason, now);
                return new Outcome(executed, next.withVersion(current.version() + 1));
            });
            if (outcome != null) {
                return outcome;
            }
        }
        throw conflict(agentId);
    }

    /** v0.0.11 🍊 Stores a decision on a pending trade (CONFLICT, rolling back the transaction, if already decided). */
    Trade decide(Trade pending, Trade.Status status, BigDecimal price, BigDecimal notional, String reason, Instant now) {
        Trade decided = pending.decided(status, price, notional, reason, now);
        if (!trades.decidePending(decided)) {
            throw (ConflictException) new ConflictException("Trade " + pending.id() + " was already decided.")
                    .with("tradeId", pending.id()).forAgent(pending.agentId().value());
        }
        return decided;
    }

    /** v0.0.11 🍊 The stored portfolio (the row exists because ensure() ran first). */
    private Portfolio load(AgentId agentId) {
        return portfolios.find(agentId).orElseThrow(() -> new IllegalStateException("Missing portfolio of " + agentId));
    }

    /** v0.0.11 🍊 CONFLICT after repeated optimistic-lock failures. */
    private static ConflictException conflict(AgentId agentId) {
        return (ConflictException) new ConflictException("The portfolio kept changing concurrently; please retry.")
                .forAgent(agentId.value());
    }
}
