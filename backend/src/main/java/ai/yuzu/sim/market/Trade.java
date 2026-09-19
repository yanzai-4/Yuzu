package ai.yuzu.sim.market;

import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/** v0.0.11 🍊 One simulated trade: requested, executed, rejected (by a human or the broker) or blocked by limits. */
public record Trade(String id, AgentId agentId, long seq, String symbol, Side side, BigDecimal qty, BigDecimal price,
                    BigDecimal notional, Status status, String cardId, String reason, Instant createdAt,
                    Instant updatedAt) {

    /** v0.0.11 🍊 Buy or sell. */
    public enum Side {
        BUY, SELL;

        /** v0.0.11 🍊 Parses "buy" / "SELL" (case-insensitive); anything else is BAD_REQUEST. */
        public static Side parse(String value) {
            String clean = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
            return switch (clean) {
                case "BUY" -> BUY;
                case "SELL" -> SELL;
                default -> throw new BadRequestException("A trade side must be BUY or SELL.");
            };
        }
    }

    /** v0.0.11 🍊 Lifecycle state (contract values). */
    public enum Status { PENDING_APPROVAL, EXECUTED, REJECTED, BLOCKED }

    /** v0.0.11 🍊 A not-yet-stored trade (id and seq are assigned by the repository). */
    static Trade draft(AgentId agentId, Order order, BigDecimal price, BigDecimal notional, Status status,
                       String cardId, String reason, Instant now) {
        return new Trade(null, agentId, 0, order.symbol(), order.side(), order.qty(), price, notional, status, cardId,
                reason, now, now);
    }

    /** v0.0.11 🍊 Copy carrying the id and sequence number assigned on insert. */
    Trade stored(String newId, long newSeq) {
        return new Trade(newId, agentId, newSeq, symbol, side, qty, price, notional, status, cardId, reason,
                createdAt, updatedAt);
    }

    /** v0.0.11 🍊 Copy after a decision on a pending trade (new status, price, notional and reason). */
    Trade decided(Status newStatus, BigDecimal newPrice, BigDecimal newNotional, String newReason, Instant now) {
        return new Trade(id, agentId, seq, symbol, side, qty, newPrice, newNotional, newStatus, cardId, newReason,
                createdAt, now);
    }

    /** v0.0.11 🍊 The order this trade was made for. */
    Order order() {
        return new Order(Instrument.require(symbol), side, qty);
    }

    /** v0.0.11 🍊 API shape (contract type {@code Trade}); the time is the last status change. */
    public TradeView toView(NaturalTime time) {
        return new TradeView(id, agentId.value(), symbol, side, qty.doubleValue(), price.doubleValue(),
                notional.doubleValue(), status, reason, time.compact(updatedAt));
    }
}
