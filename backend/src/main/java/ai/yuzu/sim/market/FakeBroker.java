package ai.yuzu.sim.market;

import ai.yuzu.common.time.NaturalTime;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/** v0.0.11 🍊 Simulated market data: deterministic per-minute quotes for CITR, LIME, YUZU, PEEL and ZEST. */
@Component
public class FakeBroker {

    private static final long MINUTE_MILLIS = 60_000L;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final NaturalTime time;

    /** v0.0.11 🍊 Injects the clock (tests use a fixed one for reproducible prices). */
    public FakeBroker(NaturalTime time) {
        this.time = time;
    }

    /** v0.0.11 🍊 Current quote of a symbol (case-insensitive); NOT_FOUND for unknown symbols. */
    public Quote quote(String symbol) {
        return quoteAt(Instrument.require(symbol), time.nowInstant());
    }

    /** v0.0.11 🍊 Current quotes of every instrument, all at the same minute. */
    public List<Quote> quotes() {
        Instant now = time.nowInstant();
        return Arrays.stream(Instrument.values()).map(instrument -> quoteAt(instrument, now)).toList();
    }

    /** v0.0.11 🍊 Quote at a given instant: the price of its minute and the change since local midnight. */
    Quote quoteAt(Instrument instrument, Instant at) {
        long minute = Math.floorDiv(at.toEpochMilli(), MINUTE_MILLIS);
        ZoneId zone = time.zone();
        Instant midnight = at.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        BigDecimal price = PriceModel.priceAt(instrument, minute);
        BigDecimal open = PriceModel.priceAt(instrument, Math.floorDiv(midnight.toEpochMilli(), MINUTE_MILLIS));
        BigDecimal change = price.subtract(open);
        BigDecimal percent = change.multiply(HUNDRED).divide(open, 2, RoundingMode.HALF_UP);
        return new Quote(instrument.symbol(), instrument.company(), price, open, change, percent,
                Instant.ofEpochMilli(minute * MINUTE_MILLIS));
    }
}
