package ai.yuzu.tool.impl.trade;

import ai.yuzu.agent.Limits;
import ai.yuzu.agent.Permission;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.sim.market.FakeBroker;
import ai.yuzu.sim.market.FakePortfolio;
import ai.yuzu.sim.market.Portfolio;
import ai.yuzu.sim.market.Trade;
import ai.yuzu.tool.spi.PermissionGuard;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.tool.spi.ToolSpec;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/** v0.0.27 🍊 Shows my simulated brokerage account: cash, positions at the current prices, limits and trades. */
@Component
public class PortfolioReadTool implements Tool<PortfolioReadTool.Args> {

    /** v0.0.27 🍊 Arguments. */
    public record Args(@Desc("How many of my latest trades to list as well (0 to 20; use 0 for none)") int recentTrades) {
    }

    /** v0.0.27 🍊 Most trades one call lists. */
    static final int MAX_TRADES = 20;

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("portfolio_read",
            "Show my simulated brokerage account: cash, the shares I hold with their average and current price, "
                    + "my trading limits, and optionally my latest trades.",
            Args.class, Set.of(Permission.TRADE_VIEW), Risk.LOW, Duration.ofSeconds(20), true,
            "my brokerage account showed");

    private final FakePortfolio portfolios;
    private final FakeBroker broker;
    private final PermissionGuard guard;
    private final NaturalTime time;

    /** v0.0.27 🍊 Injects collaborators. */
    public PortfolioReadTool(FakePortfolio portfolios, FakeBroker broker, PermissionGuard guard, NaturalTime time) {
        this.portfolios = portfolios;
        this.broker = broker;
        this.guard = guard;
        this.time = time;
    }

    /** v0.0.27 🍊 Static description. */
    @Override
    public ToolSpec<Args> spec() {
        return SPEC;
    }

    /** v0.0.27 🍊 Renders cash, positions, limits and recent trades (permission re-checked in code). */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.TRADE_VIEW);
        Portfolio account = portfolios.portfolio(ctx.agent().agentId());
        StringBuilder text = new StringBuilder("My account at " + time.compact(time.nowInstant()) + ":\nCash "
                + TradeFormat.usd(account.cash()) + ".\n");
        if (account.positions().isEmpty()) {
            text.append("I hold no shares.\n");
        } else {
            text.append("Positions:\n");
            for (Portfolio.Position position : account.positions()) {
                BigDecimal price = broker.quote(position.symbol()).price();
                text.append("- ").append(TradeFormat.qty(position.qty())).append(' ').append(position.symbol())
                        .append(", average ").append(TradeFormat.usd(position.avgPrice())).append(", now ")
                        .append(TradeFormat.usd(price)).append(" (worth ")
                        .append(TradeFormat.usd(price.multiply(position.qty())
                                .setScale(2, java.math.RoundingMode.HALF_UP))).append(")\n");
            }
        }
        text.append(limits(ctx.profile().scope().limits()));
        int wanted = Math.max(0, Math.min(args.recentTrades(), MAX_TRADES));
        if (wanted > 0) {
            List<Trade> trades = portfolios.trades(ctx.agent().agentId(), wanted);
            text.append(trades.isEmpty() ? "\nI have not traded yet." : "\nMy latest trades:");
            trades.forEach(trade -> text.append("\n- ").append(TradeFormat.order(trade)).append(" — ")
                    .append(trade.status()).append(", ").append(time.compact(trade.updatedAt()))
                    .append(trade.reason() == null ? "" : " (" + trade.reason() + ")"));
        }
        return ToolResult.ok(text.toString().strip(), time.nowInstant());
    }

    /** v0.0.27 🍊 My trading limits in words, so I never plan a trade I am not allowed to place. */
    private static String limits(Limits limits) {
        return "My limits: at most " + TradeFormat.usd(BigDecimal.valueOf(limits.tradeMaxNotionalUsd()))
                + " per trade, and anything above "
                + TradeFormat.usd(BigDecimal.valueOf(limits.tradeAutoApproveUsd()))
                + " needs a human approval first.\n";
    }
}
