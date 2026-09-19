package ai.yuzu.sim.market;

import ai.yuzu.agent.Limits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.11 🍊 Pure trade rule: inclusive thresholds, disabled trading, invalid notionals and readable reasons. */
class TradeRulesTest {

    private static final Limits ANALYST = new Limits(List.of(), 10, 1_000, 200, 50);

    /** v0.0.11 🍊 ≤ auto-approve executes, ≤ max needs approval, above max is blocked (both limits inclusive). */
    @ParameterizedTest
    @CsvSource({"0.01, EXECUTE", "199.99, EXECUTE", "200.00, EXECUTE", "200.01, NEEDS_APPROVAL",
            "999.99, NEEDS_APPROVAL", "1000.00, NEEDS_APPROVAL", "1000.01, BLOCKED", "25000, BLOCKED"})
    void thresholdsAreInclusive(String notional, TradeRules.Verdict expected) {
        assertThat(TradeRules.decide(ANALYST, new BigDecimal(notional)).verdict()).isEqualTo(expected);
    }

    /** v0.0.11 🍊 Zero, negative, missing, NaN and infinite notionals are blocked. */
    @Test
    void invalidNotionalsAreBlocked() {
        assertThat(TradeRules.decide(ANALYST, BigDecimal.ZERO).blocked()).isTrue();
        assertThat(TradeRules.decide(ANALYST, new BigDecimal("-5")).blocked()).isTrue();
        assertThat(TradeRules.decide(ANALYST, (BigDecimal) null).blocked()).isTrue();
        assertThat(TradeRules.decide(ANALYST, Double.NaN).blocked()).isTrue();
        assertThat(TradeRules.decide(ANALYST, Double.POSITIVE_INFINITY).blocked()).isTrue();
        assertThat(TradeRules.decide(ANALYST, 150.0).executable()).isTrue();
    }

    /** v0.0.11 🍊 A maximum of 0 (the default) disables trading entirely. */
    @Test
    void zeroMaximumDisablesTrading() {
        TradeRules.Decision decision = TradeRules.decide(Limits.defaults(), new BigDecimal("1"));
        assertThat(decision.blocked()).isTrue();
        assertThat(decision.reason()).contains("disabled");
        assertThat(TradeRules.decide(null, new BigDecimal("1")).blocked()).isTrue();
    }

    /** v0.0.11 🍊 An auto-approve limit of 0 sends every trade to a human (up to the maximum). */
    @Test
    void zeroAutoApproveNeedsApprovalForEverything() {
        Limits cautious = new Limits(List.of(), 10, 500, 0, 50);
        assertThat(TradeRules.decide(cautious, new BigDecimal("0.01")).needsApproval()).isTrue();
        assertThat(TradeRules.decide(cautious, new BigDecimal("500")).needsApproval()).isTrue();
        assertThat(TradeRules.decide(cautious, new BigDecimal("500.01")).blocked()).isTrue();
    }

    /** v0.0.11 🍊 An auto-approve limit above the maximum is clamped by Limits, so the maximum still wins. */
    @Test
    void autoApproveCannotExceedTheMaximum() {
        Limits odd = new Limits(List.of(), 10, 100, 1_000, 50);
        assertThat(TradeRules.decide(odd, new BigDecimal("100")).executable()).isTrue();
        assertThat(TradeRules.decide(odd, new BigDecimal("150")).blocked()).isTrue();
    }

    /** v0.0.11 🍊 Reasons state the amounts in dollars. */
    @Test
    void reasonsAreReadable() {
        assertThat(TradeRules.decide(ANALYST, new BigDecimal("1250")).reason())
                .isEqualTo("Notional $1,250.00 exceeds the per-trade maximum of $1,000.00.");
        assertThat(TradeRules.decide(ANALYST, new BigDecimal("450")).reason())
                .contains("$450.00").contains("$200.00").contains("human approval");
        assertThat(TradeRules.decide(ANALYST, new BigDecimal("50")).reason()).contains("within the auto-approve limit");
    }
}
