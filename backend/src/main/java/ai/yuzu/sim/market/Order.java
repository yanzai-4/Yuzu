package ai.yuzu.sim.market;

import ai.yuzu.common.error.BadRequestException;

import java.math.BigDecimal;

/** v0.0.11 🍊 A validated order: known instrument, a side, and a positive quantity with at most 4 decimals. */
record Order(Instrument instrument, Trade.Side side, BigDecimal qty) {

    /** v0.0.11 🍊 Largest quantity of one order. */
    static final BigDecimal MAX_QTY = new BigDecimal("1000000");

    /** v0.0.11 🍊 Decimal places of stored quantities (DECIMAL(18,4)). */
    static final int QTY_SCALE = 4;

    /** v0.0.11 🍊 Validates raw input: NOT_FOUND for unknown symbols, BAD_REQUEST for a bad side or quantity. */
    static Order of(String symbol, Trade.Side side, BigDecimal qty) {
        Instrument instrument = Instrument.require(symbol);
        if (side == null) {
            throw new BadRequestException("A trade needs a side: BUY or SELL.");
        }
        if (qty == null || qty.signum() <= 0) {
            throw new BadRequestException("The quantity must be positive.");
        }
        if (qty.stripTrailingZeros().scale() > QTY_SCALE) {
            throw new BadRequestException("The quantity can have at most " + QTY_SCALE + " decimal places.");
        }
        if (qty.compareTo(MAX_QTY) > 0) {
            throw new BadRequestException("The quantity can be at most " + MarketFormat.qty(MAX_QTY) + ".");
        }
        return new Order(instrument, side, qty.setScale(QTY_SCALE));
    }

    /** v0.0.11 🍊 The instrument's symbol. */
    String symbol() {
        return instrument.symbol();
    }
}
