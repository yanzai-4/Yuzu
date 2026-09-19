package ai.yuzu.tool.impl.trade;

import ai.yuzu.sim.market.Quote;
import ai.yuzu.sim.market.Trade;

import java.math.BigDecimal;
import java.util.Locale;

/** v0.0.27 🍊 English rendering of money, quantities, quotes and trades inside the trading tools. */
final class TradeFormat {

    /** v0.0.27 🍊 Static helpers only. */
    private TradeFormat() {
    }

    /** v0.0.27 🍊 "$1,250.00". */
    static String usd(BigDecimal value) {
        return String.format(Locale.US, "$%,.2f", value);
    }

    /** v0.0.27 🍊 Quantity without trailing zeros ("2.5", "10"). */
    static String qty(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** v0.0.27 🍊 "YUZU (Yuzu Labs) $128.44, +1.20 (+0.94%) since the open". */
    static String quote(Quote quote) {
        return quote.symbol() + " (" + quote.company() + ") " + usd(quote.price()) + ", "
                + (quote.change().signum() < 0 ? "" : "+") + usd(quote.change()) + " ("
                + (quote.changePercent().signum() < 0 ? "" : "+") + quote.changePercent() + "%) since the open";
    }

    /** v0.0.27 🍊 "BUY 5 YUZU at $128.44 (notional $642.20)". */
    static String order(Trade trade) {
        return trade.side() + " " + qty(trade.qty()) + " " + trade.symbol() + " at " + usd(trade.price())
                + " (notional " + usd(trade.notional()) + ")";
    }
}
