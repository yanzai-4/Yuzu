package ai.yuzu.sim.market;

import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.11 🍊 Deterministic per-minute quotes that move over time and stay within the model's hard bounds. */
class FakeBrokerTest {

    private static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");
    private static final Instant START = Instant.parse("2026-09-19T18:32:05Z");

    private final MutableClock clock = new MutableClock(START);
    private final FakeBroker broker = new FakeBroker(new NaturalTime(clock, ZONE));

    /** v0.0.11 🍊 Within a minute the price is fixed, and any broker instance agrees on it. */
    @Test
    void quotesAreDeterministicWithinAMinute() {
        Quote first = broker.quote("YUZU");
        clock.advance(Duration.ofSeconds(20));
        Quote later = broker.quote("YUZU");
        assertThat(later.price()).isEqualTo(first.price());
        assertThat(later.asOf()).isEqualTo(Instant.parse("2026-09-19T18:32:00Z"));
        FakeBroker other = new FakeBroker(new NaturalTime(new MutableClock(START), ZONE));
        assertThat(other.quote("YUZU")).isEqualTo(first);
    }

    /** v0.0.11 🍊 Prices move from minute to minute like a random walk. */
    @Test
    void pricesMoveOverTime() {
        Set<BigDecimal> seen = new HashSet<>();
        for (int minute = 0; minute < 120; minute++) {
            seen.add(broker.quote("CITR").price());
            clock.advance(Duration.ofMinutes(1));
        }
        assertThat(seen).hasSizeGreaterThan(20);
    }

    /** v0.0.11 🍊 Over a simulated week every price stays within the model bound (and within ±40% of base). */
    @Test
    void pricesStayWithinTheModelBounds() {
        long startMinute = START.toEpochMilli() / 60_000;
        for (Instrument instrument : Instrument.values()) {
            double base = instrument.basePrice().doubleValue();
            double bound = PriceModel.maxLogMove(instrument);
            assertThat(bound).isLessThan(Math.log(1.4));
            for (long minute = startMinute; minute < startMinute + 7 * 24 * 60; minute += 7) {
                double price = PriceModel.priceAt(instrument, minute).doubleValue();
                assertThat(price).isBetween(base * Math.exp(-bound) - 0.01, base * Math.exp(bound) + 0.01);
            }
        }
    }

    /** v0.0.11 🍊 Symbols are case-insensitive; unknown ones are NOT_FOUND with the list of valid symbols. */
    @Test
    void symbolsAreCaseInsensitiveAndUnknownOnesAreNotFound() {
        assertThat(broker.quote(" yuzu ").symbol()).isEqualTo("YUZU");
        assertThat(broker.quote("yuzu").company()).isEqualTo("Yuzu Labs");
        assertThatThrownBy(() -> broker.quote("AAPL")).isInstanceOf(NotFoundException.class)
                .hasMessageContaining("CITR, LIME, YUZU, PEEL, ZEST");
        assertThatThrownBy(() -> broker.quote(null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> broker.quote("BAD\nSYMBOL")).isInstanceOf(NotFoundException.class)
                .hasMessageNotContaining("\n");
    }

    /** v0.0.11 🍊 quotes() covers every instrument at the same minute. */
    @Test
    void quotesCoverEveryInstrument() {
        List<Quote> quotes = broker.quotes();
        assertThat(quotes).extracting(Quote::symbol).containsExactly("CITR", "LIME", "YUZU", "PEEL", "ZEST");
        assertThat(quotes).extracting(Quote::asOf).containsOnly(Instant.parse("2026-09-19T18:32:00Z"));
    }

    /** v0.0.11 🍊 The change is measured from the price at local midnight. */
    @Test
    void changeIsMeasuredFromLocalMidnight() {
        Quote now = broker.quote("ZEST");
        assertThat(now.change()).isEqualByComparingTo(now.price().subtract(now.open()));
        clock.set(Instant.parse("2026-09-19T07:00:00Z"));
        assertThat(broker.quote("ZEST").price()).isEqualByComparingTo(now.open());
    }

    /** v0.0.11 🍊 Notional = quantity × price rounded to cents. */
    @Test
    void notionalRoundsToCents() {
        Quote quote = new Quote("CITR", "Citrus Holdings", new BigDecimal("42.13"), new BigDecimal("42.00"),
                new BigDecimal("0.13"), new BigDecimal("0.31"), START);
        assertThat(quote.notional(new BigDecimal("3.3333"))).isEqualByComparingTo("140.43");
        assertThat(quote.notional(new BigDecimal("3.3333")).scale()).isEqualTo(2);
    }
}
