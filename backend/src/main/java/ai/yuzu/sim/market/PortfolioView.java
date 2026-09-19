package ai.yuzu.sim.market;

import java.util.List;

/** v0.0.11 🍊 API shape of a simulated portfolio (contract type {@code Portfolio}). */
public record PortfolioView(String agentId, double cash, List<PositionView> positions) {

    /** v0.0.11 🍊 API shape of one position ({@code { symbol, qty, avgPrice }}). */
    public record PositionView(String symbol, double qty, double avgPrice) {
    }
}
