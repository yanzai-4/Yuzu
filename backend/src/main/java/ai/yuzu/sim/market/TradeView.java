package ai.yuzu.sim.market;

/** v0.0.11 🍊 API shape of a simulated trade (contract type {@code Trade}); money and quantities are numbers. */
public record TradeView(String id, String agentId, String symbol, Trade.Side side, double qty, double price,
                        double notional, Trade.Status status, String reason, String time) {
}
