package ai.yuzu.tool.impl.trade;

import ai.yuzu.agent.Permission;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.Nullable;
import ai.yuzu.sim.market.FakeBroker;
import ai.yuzu.sim.market.Instrument;
import ai.yuzu.sim.market.Quote;
import ai.yuzu.tool.spi.PermissionGuard;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.tool.spi.ToolSpec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/** v0.0.27 🍊 Looks up the current simulated price of one listed company, or of the whole board. */
@Component
public class MarketQuoteTool implements Tool<MarketQuoteTool.Args> {

    /** v0.0.27 🍊 Arguments. */
    public record Args(@Nullable @Desc("Ticker symbol such as YUZU; null shows every listed company") String symbol) {
    }

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("market_quote",
            "Look up the current price of a listed company (" + String.join(", ", Instrument.symbols())
                    + ") and its change since the market opened; leave the symbol empty for the whole board.",
            Args.class, Set.of(Permission.TRADE_VIEW), Risk.LOW, Duration.ofSeconds(15), true,
            "the market data showed");

    private final FakeBroker broker;
    private final PermissionGuard guard;
    private final NaturalTime time;

    /** v0.0.27 🍊 Injects collaborators. */
    public MarketQuoteTool(FakeBroker broker, PermissionGuard guard, NaturalTime time) {
        this.broker = broker;
        this.guard = guard;
        this.time = time;
    }

    /** v0.0.27 🍊 Static description. */
    @Override
    public ToolSpec<Args> spec() {
        return SPEC;
    }

    /** v0.0.27 🍊 One quote or the whole board (permission re-checked in code). */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.TRADE_VIEW);
        String symbol = args.symbol() == null ? "" : args.symbol().strip();
        List<Quote> quotes = symbol.isEmpty() ? broker.quotes() : List.of(broker.quote(symbol));
        StringBuilder text = new StringBuilder(symbol.isEmpty()
                ? "The market at " + time.compact(time.nowInstant()) + ":\n"
                : "Quote at " + time.compact(time.nowInstant()) + ":\n");
        quotes.forEach(quote -> text.append("- ").append(TradeFormat.quote(quote)).append('\n'));
        return ToolResult.ok(text.toString().strip(), time.nowInstant());
    }
}
