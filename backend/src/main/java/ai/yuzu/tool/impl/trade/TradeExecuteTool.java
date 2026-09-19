package ai.yuzu.tool.impl.trade;

import ai.yuzu.agent.Permission;
import ai.yuzu.card.CardView;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.sim.market.FakePortfolio;
import ai.yuzu.sim.market.Instrument;
import ai.yuzu.sim.market.Trade;
import ai.yuzu.tool.spi.ApprovalGate;
import ai.yuzu.tool.spi.PermissionGuard;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.tool.spi.ToolSpec;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

/**
 * v0.0.27 🍊 Places a simulated trade. Small ones settle at once; anything above my auto-approve limit waits
 * for a human.
 *
 * <p>Every money rule stays in the simulated broker: {@link FakePortfolio#executeTrade} blocks a trade over the
 * hard maximum (BLOCKED row + {@code PERMISSION_DENIED}, which no approval can lift), refuses a trade without
 * cash or shares (REJECTED row), and parks anything above {@code tradeAutoApproveUsd} as PENDING_APPROVAL. The
 * tool's only job is to show that pending trade on an {@link ApprovalGate} card and wait — it can neither
 * settle a parked trade nor skip the card.</p>
 */
@Component
public class TradeExecuteTool implements Tool<TradeExecuteTool.Args> {

    /** v0.0.27 🍊 Arguments. */
    public record Args(@Desc("Ticker symbol to trade, one of CITR, LIME, YUZU, PEEL, ZEST") String symbol,
                       @Desc("BUY or SELL") Trade.Side side,
                       @Desc("How many shares (positive, at most 4 decimal places)") double quantity) {
    }

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("trade_execute",
            "Place a simulated trade (" + String.join(", ", Instrument.symbols()) + "). Trades within my "
                    + "auto-approve limit settle right away; larger ones wait for a human approval card, and "
                    + "anything above my maximum is refused outright.",
            Args.class, Set.of(Permission.TRADE_EXECUTE), Risk.HIGH, Duration.ofSeconds(30), true,
            "the broker confirmed");

    private final FakePortfolio portfolios;
    private final ApprovalGate approvals;
    private final PermissionGuard guard;
    private final Jsons jsons;
    private final NaturalTime time;

    /** v0.0.27 🍊 Injects collaborators. */
    public TradeExecuteTool(FakePortfolio portfolios, ApprovalGate approvals, PermissionGuard guard, Jsons jsons,
                            NaturalTime time) {
        this.portfolios = portfolios;
        this.approvals = approvals;
        this.guard = guard;
        this.jsons = jsons;
        this.time = time;
    }

    /** v0.0.27 🍊 Static description. */
    @Override
    public ToolSpec<Args> spec() {
        return SPEC;
    }

    /** v0.0.27 🍊 Asks the broker to trade; a parked trade gets an approval card and the call waits. */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.TRADE_EXECUTE);
        if (args.side() == null) {
            return ToolResult.error("A trade needs a side: BUY or SELL.", time.nowInstant());
        }
        if (!Double.isFinite(args.quantity()) || args.quantity() <= 0) {
            return ToolResult.error("The quantity must be a positive number of shares.", time.nowInstant());
        }
        Trade trade = portfolios.executeTrade(ctx.agent().agentId(), args.symbol(), args.side(),
                BigDecimal.valueOf(args.quantity()));
        if (trade.status() != Trade.Status.PENDING_APPROVAL) {
            return ToolResult.ok("Done: " + TradeFormat.order(trade) + " at " + time.compact(trade.updatedAt())
                    + ". Trade id " + trade.id() + ".", time.nowInstant());
        }
        CardView card = approvals.request(ctx, TradeApprovalHandler.PURPOSE, prompt(trade),
                jsons.write(new PendingTrade(trade.id())));
        return ToolResult.waiting("This trade is too large to place on my own, so I asked the humans to approve "
                + TradeFormat.order(trade) + " (trade " + trade.id() + ", approval card " + card.id()
                + "). Nothing was bought or sold yet; I will hear about it when somebody decides.",
                time.nowInstant());
    }

    /** v0.0.27 🍊 What the human sees on the approval card: the order, why it needs them, and the price used. */
    private String prompt(Trade trade) {
        return "Approve this simulated trade?\n" + TradeFormat.order(trade)
                + (trade.reason() == null ? "" : "\n" + trade.reason())
                + "\nPriced at " + time.compact(trade.createdAt())
                + "; it settles at the price of the moment you approve it.";
    }
}
