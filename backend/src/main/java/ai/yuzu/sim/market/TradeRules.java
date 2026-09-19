package ai.yuzu.sim.market;

import ai.yuzu.agent.Limits;

import java.math.BigDecimal;

import static ai.yuzu.sim.market.MarketFormat.usd;

/** v0.0.11 🍊 Pure trade limit rule: EXECUTE up to the auto-approve limit, NEEDS_APPROVAL up to the max, else BLOCKED. */
public final class TradeRules {

    /** v0.0.11 🍊 What may happen with a trade of a given notional. */
    public enum Verdict { EXECUTE, NEEDS_APPROVAL, BLOCKED }

    /** v0.0.11 🍊 A verdict plus an English explanation for the agent, the approval card and the UI. */
    public record Decision(Verdict verdict, String reason) {

        /** v0.0.11 🍊 True when the trade may run without a human. */
        public boolean executable() {
            return verdict == Verdict.EXECUTE;
        }

        /** v0.0.11 🍊 True when a human approval card is required first. */
        public boolean needsApproval() {
            return verdict == Verdict.NEEDS_APPROVAL;
        }

        /** v0.0.11 🍊 True when the trade must not run at all. */
        public boolean blocked() {
            return verdict == Verdict.BLOCKED;
        }
    }

    /** v0.0.11 🍊 Static rules only. */
    private TradeRules() {
    }

    /** v0.0.11 🍊 Decides a notional (USD) against tradeAutoApproveUsd (inclusive) and tradeMaxNotionalUsd (inclusive). */
    public static Decision decide(Limits limits, BigDecimal notionalUsd) {
        Limits effective = limits == null ? Limits.defaults() : limits;
        if (notionalUsd == null || notionalUsd.signum() <= 0) {
            return new Decision(Verdict.BLOCKED, "The trade notional must be a positive dollar amount.");
        }
        BigDecimal max = BigDecimal.valueOf(effective.tradeMaxNotionalUsd());
        BigDecimal auto = BigDecimal.valueOf(effective.tradeAutoApproveUsd());
        if (max.signum() <= 0) {
            return new Decision(Verdict.BLOCKED, "Trading is disabled for this agent (its maximum trade is $0).");
        }
        if (notionalUsd.compareTo(max) > 0) {
            return new Decision(Verdict.BLOCKED, "Notional " + usd(notionalUsd) + " exceeds the per-trade maximum of "
                    + usd(max) + ".");
        }
        if (notionalUsd.compareTo(auto) <= 0) {
            return new Decision(Verdict.EXECUTE, "Notional " + usd(notionalUsd)
                    + " is within the auto-approve limit of " + usd(auto) + ".");
        }
        return new Decision(Verdict.NEEDS_APPROVAL, "Notional " + usd(notionalUsd) + " is above the auto-approve limit of "
                + usd(auto) + " and needs a human approval (maximum " + usd(max) + ").");
    }

    /** v0.0.11 🍊 Same rule for a double notional; NaN and infinities are BLOCKED. */
    public static Decision decide(Limits limits, double notionalUsd) {
        if (Double.isNaN(notionalUsd) || Double.isInfinite(notionalUsd)) {
            return new Decision(Verdict.BLOCKED, "The trade notional must be a positive dollar amount.");
        }
        return decide(limits, BigDecimal.valueOf(notionalUsd));
    }
}
