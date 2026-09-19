package ai.yuzu.sim.market;

import ai.yuzu.common.id.AgentId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static ai.yuzu.sim.market.MarketFormat.qty;
import static ai.yuzu.sim.market.MarketFormat.usd;

/** v0.0.11 🍊 An agent's simulated account: cash plus positions; the trade math lives here and is pure. */
public record Portfolio(AgentId agentId, BigDecimal cash, List<Position> positions, int version, Instant updatedAt) {

    /** v0.0.11 🍊 Cash every agent starts with. */
    public static final BigDecimal STARTING_CASH = new BigDecimal("10000.00");

    /** v0.0.11 🍊 Shares held in one symbol and their average purchase price. */
    public record Position(String symbol, BigDecimal qty, BigDecimal avgPrice) {
    }

    /** v0.0.11 🍊 Sorts positions by symbol so the portfolio renders the same way every time. */
    public Portfolio {
        positions = positions.stream().sorted(Comparator.comparing(Position::symbol)).toList();
    }

    /** v0.0.11 🍊 The untouched account: $10,000 cash, no positions, version 0. */
    public static Portfolio starting(AgentId agentId, Instant now) {
        return new Portfolio(agentId, STARTING_CASH, List.of(), 0, now);
    }

    /** v0.0.11 🍊 Shares held in a symbol (0 when none). */
    public BigDecimal quantityOf(String symbol) {
        return positions.stream().filter(p -> p.symbol().equals(symbol)).map(Position::qty).findFirst()
                .orElse(BigDecimal.ZERO);
    }

    /** v0.0.11 🍊 Why the trade cannot settle (no negative cash, no short selling), or empty when it can. */
    public Optional<String> rejectionFor(Trade.Side side, String symbol, BigDecimal quantity, BigDecimal notional) {
        if (side == Trade.Side.BUY && cash.compareTo(notional) < 0) {
            return Optional.of("Insufficient cash: buying " + qty(quantity) + " " + symbol + " needs " + usd(notional)
                    + " but only " + usd(cash) + " is available.");
        }
        if (side == Trade.Side.SELL) {
            BigDecimal held = quantityOf(symbol);
            if (held.compareTo(quantity) < 0) {
                return Optional.of("Cannot sell " + qty(quantity) + " " + symbol + ": "
                        + (held.signum() == 0 ? "no shares are held" : "only " + qty(held) + " held")
                        + " (short selling is not allowed).");
            }
        }
        return Optional.empty();
    }

    /** v0.0.11 🍊 The portfolio after the trade settles (callers check rejectionFor first). */
    public Portfolio apply(Trade.Side side, String symbol, BigDecimal quantity, BigDecimal price, BigDecimal notional,
                           Instant now) {
        rejectionFor(side, symbol, quantity, notional).ifPresent(reason -> {
            throw new IllegalStateException(reason);
        });
        List<Position> next = new ArrayList<>();
        Position current = null;
        for (Position position : positions) {
            if (position.symbol().equals(symbol)) {
                current = position;
            } else {
                next.add(position);
            }
        }
        BigDecimal nextCash;
        if (side == Trade.Side.BUY) {
            nextCash = cash.subtract(notional);
            BigDecimal heldQty = current == null ? BigDecimal.ZERO : current.qty();
            BigDecimal heldCost = current == null ? BigDecimal.ZERO : current.qty().multiply(current.avgPrice());
            BigDecimal newQty = heldQty.add(quantity);
            BigDecimal avgPrice = heldCost.add(quantity.multiply(price)).divide(newQty, 4, RoundingMode.HALF_UP);
            next.add(new Position(symbol, newQty.setScale(4, RoundingMode.HALF_UP), avgPrice));
        } else {
            nextCash = cash.add(notional);
            BigDecimal left = current.qty().subtract(quantity);
            if (left.signum() > 0) {
                next.add(new Position(symbol, left.setScale(4, RoundingMode.HALF_UP), current.avgPrice()));
            }
        }
        return new Portfolio(agentId, nextCash.setScale(2, RoundingMode.HALF_UP), next, version, now);
    }

    /** v0.0.11 🍊 Copy with another optimistic-lock version. */
    Portfolio withVersion(int newVersion) {
        return new Portfolio(agentId, cash, positions, newVersion, updatedAt);
    }

    /** v0.0.11 🍊 API shape (contract type {@code Portfolio}). */
    public PortfolioView toView() {
        return new PortfolioView(agentId.value(), cash.doubleValue(), positions.stream()
                .map(p -> new PortfolioView.PositionView(p.symbol(), p.qty().doubleValue(), p.avgPrice().doubleValue()))
                .toList());
    }
}
