package ai.yuzu.sim.market;

import java.math.BigDecimal;
import java.util.Locale;

/** v0.0.11 🍊 English formatting of money and quantities inside trade explanations. */
final class MarketFormat {

    /** v0.0.11 🍊 Static helpers only. */
    private MarketFormat() {
    }

    /** v0.0.11 🍊 "$1,250.00". */
    static String usd(BigDecimal value) {
        return String.format(Locale.US, "$%,.2f", value);
    }

    /** v0.0.11 🍊 Quantity without trailing zeros ("2.5", "10"). */
    static String qty(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
