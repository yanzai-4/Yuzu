package ai.yuzu.sim.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** v0.0.11 🍊 A simulated quote: current price, the day's opening price and the change since the open. */
public record Quote(String symbol, String company, BigDecimal price, BigDecimal open, BigDecimal change,
                    BigDecimal changePercent, Instant asOf) {

    /** v0.0.11 🍊 Notional value of a quantity at this price, rounded to cents. */
    public BigDecimal notional(BigDecimal qty) {
        return qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }
}
