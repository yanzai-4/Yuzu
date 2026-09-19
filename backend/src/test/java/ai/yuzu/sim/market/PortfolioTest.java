package ai.yuzu.sim.market;

import ai.yuzu.common.id.AgentId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static ai.yuzu.sim.market.Trade.Side.BUY;
import static ai.yuzu.sim.market.Trade.Side.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.11 🍊 Pure portfolio math: cash, weighted average price, closing positions, no negative cash, no shorting. */
class PortfolioTest {

    private static final AgentId AGENT = AgentId.of("agent-f00d");
    private static final Instant NOW = Instant.parse("2026-09-19T18:00:00Z");

    /** v0.0.11 🍊 Every account starts with $10,000 and nothing else. */
    @Test
    void startsWithTenThousandDollars() {
        Portfolio start = Portfolio.starting(AGENT, NOW);
        assertThat(start.cash()).isEqualByComparingTo("10000");
        assertThat(start.positions()).isEmpty();
        assertThat(start.version()).isZero();
        assertThat(start.quantityOf("CITR")).isEqualByComparingTo("0");
    }

    /** v0.0.11 🍊 Buying twice moves cash and averages the purchase price by quantity. */
    @Test
    void buyingAveragesThePrice() {
        Portfolio after = Portfolio.starting(AGENT, NOW)
                .apply(BUY, "CITR", dec("10"), dec("40.00"), dec("400.00"), NOW)
                .apply(BUY, "CITR", dec("30"), dec("44.00"), dec("1320.00"), NOW);
        assertThat(after.cash()).isEqualByComparingTo("8280.00");
        assertThat(after.quantityOf("CITR")).isEqualByComparingTo("40");
        assertThat(after.positions()).singleElement().satisfies(p -> {
            assertThat(p.avgPrice()).isEqualByComparingTo("43.0000");
            assertThat(p.qty().scale()).isEqualTo(4);
        });
    }

    /** v0.0.11 🍊 Selling keeps the average price; selling everything closes the position. */
    @Test
    void sellingKeepsTheAverageAndClosesPositions() {
        Portfolio bought = Portfolio.starting(AGENT, NOW)
                .apply(BUY, "LIME", dec("10"), dec("18.00"), dec("180.00"), NOW)
                .apply(BUY, "ZEST", dec("1"), dec("63.00"), dec("63.00"), NOW);
        Portfolio partly = bought.apply(SELL, "LIME", dec("4"), dec("20.00"), dec("80.00"), NOW);
        assertThat(partly.cash()).isEqualByComparingTo("9837.00");
        assertThat(partly.quantityOf("LIME")).isEqualByComparingTo("6");
        assertThat(partly.positions()).extracting(Portfolio.Position::symbol).containsExactly("LIME", "ZEST");
        assertThat(partly.positions().get(0).avgPrice()).isEqualByComparingTo("18.00");
        Portfolio closed = partly.apply(SELL, "LIME", dec("6"), dec("20.00"), dec("120.00"), NOW);
        assertThat(closed.positions()).extracting(Portfolio.Position::symbol).containsExactly("ZEST");
        assertThat(closed.cash()).isEqualByComparingTo("9957.00");
    }

    /** v0.0.11 🍊 A buy larger than the cash is rejected; spending exactly all cash is fine. */
    @Test
    void cashNeverGoesNegative() {
        Portfolio start = Portfolio.starting(AGENT, NOW);
        assertThat(start.rejectionFor(BUY, "YUZU", dec("100"), dec("12800.00")))
                .hasValueSatisfying(reason -> assertThat(reason).startsWith("Insufficient cash")
                        .contains("$12,800.00").contains("$10,000.00"));
        assertThatThrownBy(() -> start.apply(BUY, "YUZU", dec("100"), dec("128.00"), dec("12800.00"), NOW))
                .isInstanceOf(IllegalStateException.class);
        Portfolio allIn = start.apply(BUY, "PEEL", dec("1000"), dec("10.00"), dec("10000.00"), NOW);
        assertThat(allIn.cash()).isEqualByComparingTo("0.00");
    }

    /** v0.0.11 🍊 Selling more than held (or anything not held) is short selling and is rejected. */
    @Test
    void shortSellingIsRejected() {
        Portfolio start = Portfolio.starting(AGENT, NOW);
        assertThat(start.rejectionFor(SELL, "LIME", dec("1"), dec("18.50")))
                .hasValueSatisfying(reason -> assertThat(reason).contains("no shares are held").contains("short selling"));
        Portfolio two = start.apply(BUY, "LIME", dec("2"), dec("18.50"), dec("37.00"), NOW);
        assertThat(two.rejectionFor(SELL, "LIME", dec("3"), dec("55.50")))
                .hasValueSatisfying(reason -> assertThat(reason).contains("only 2 held"));
        assertThat(two.rejectionFor(SELL, "LIME", dec("2"), dec("37.00"))).isEmpty();
    }

    /** v0.0.11 🍊 The view carries exactly the contract fields as numbers. */
    @Test
    void viewMatchesTheContract() {
        PortfolioView view = Portfolio.starting(AGENT, NOW)
                .apply(BUY, "CITR", dec("2.5"), dec("42.10"), dec("105.25"), NOW).toView();
        assertThat(view.agentId()).isEqualTo("agent-f00d");
        assertThat(view.cash()).isEqualTo(9894.75);
        assertThat(view.positions()).singleElement()
                .isEqualTo(new PortfolioView.PositionView("CITR", 2.5, 42.1));
    }

    /** v0.0.11 🍊 Shorthand for BigDecimal literals. */
    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }
}
