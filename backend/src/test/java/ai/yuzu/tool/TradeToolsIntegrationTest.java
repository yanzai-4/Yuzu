package ai.yuzu.tool;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Limits;
import ai.yuzu.agent.Permission;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.card.CardService;
import ai.yuzu.card.CardView;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.room.UserView;
import ai.yuzu.settings.LlmProvider;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.sim.market.FakePortfolio;
import ai.yuzu.sim.market.Portfolio;
import ai.yuzu.sim.market.Trade;
import ai.yuzu.support.FakeLlmServer;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.TestRooms;
import ai.yuzu.tool.impl.trade.MarketQuoteTool;
import ai.yuzu.tool.impl.trade.PortfolioReadTool;
import ai.yuzu.tool.impl.trade.TradeExecuteTool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

import static ai.yuzu.support.FakeLlmServer.completion;
import static ai.yuzu.support.FakeLlmServer.schema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.27 🍊 Trading tools: quotes and portfolio reads, auto-approved trades, approval cards and hard limits. */
@IntegrationTest
class TradeToolsIntegrationTest {

    @Autowired
    private AgentService agents;
    @Autowired
    private HumanUserService humans;
    @Autowired
    private SettingsService settings;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private MarketQuoteTool quoteTool;
    @Autowired
    private PortfolioReadTool portfolioTool;
    @Autowired
    private TradeExecuteTool tradeTool;
    @Autowired
    private FakePortfolio portfolios;
    @Autowired
    private CardService cards;
    @Autowired
    private AgentRuntimeManager runtimes;
    @Autowired
    private ModuleDeps deps;
    @Autowired
    private NaturalTime time;

    private FakeLlmServer server;
    private String roomId;
    private AgentProfile analyst;
    private AgentProfile researcher;
    private UserView alice;

    /** v0.0.27 🍊 One isolated room with a finance analyst ($1,000 max, $200 auto-approve) and a human. */
    @BeforeEach
    void setUp() throws Exception {
        server = new FakeLlmServer();
        server.defaultFor(schema("main"), completion(
                "{\"thought\":\"I noted the outcome\",\"mode\":\"END\",\"actions\":[],\"nextThought\":null}",
                1800, 1024, 20));
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-9911");
        roomId = TestRooms.create(jdbc);
        analyst = agents.createNamed(roomId, new CreateAgentRequest(Role.FINANCE_ANALYST, null, null, null, null,
                new Limits.LimitsPatch(null, null, 1_000d, 200d, null)), "Yuzu");
        researcher = agents.createNamed(roomId, new CreateAgentRequest(Role.RESEARCHER, null, null, null,
                List.of(Permission.CHAT_POST), null), "Lime");
        alice = humans.join(roomId, "Alice");
    }

    @AfterEach
    void tearDown() {
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.27 🍊 market_quote shows one symbol or the whole board; portfolio_read shows cash and positions. */
    @Test
    void readsQuotesAndPortfolio() {
        ToolResult one = quoteTool.execute(context(analyst), new MarketQuoteTool.Args("peel"));
        assertThat(one.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(one.output()).contains("PEEL").contains("Peel Packaging").contains("$");

        ToolResult board = quoteTool.execute(context(analyst), new MarketQuoteTool.Args(null));
        assertThat(board.output()).contains("CITR").contains("LIME").contains("YUZU").contains("ZEST");

        ToolResult account = portfolioTool.execute(context(analyst), new PortfolioReadTool.Args(5));
        assertThat(account.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(account.output()).contains("$10,000.00");
    }

    /** v0.0.27 🍊 A trade inside the auto-approve limit executes at once and moves cash. */
    @Test
    void executesTradeInsideTheAutoApproveLimit() {
        ToolResult result = tradeTool.execute(context(analyst),
                new TradeExecuteTool.Args("PEEL", Trade.Side.BUY, 10));

        assertThat(result.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(result.output()).contains("PEEL").contains("10");
        assertThat(portfolios.trades(analyst.agentId(), 10)).singleElement()
                .extracting(Trade::status).isEqualTo(Trade.Status.EXECUTED);
        assertThat(portfolios.portfolio(analyst.agentId()).cash()).isLessThan(new BigDecimal("10000.00"));
    }

    /** v0.0.27 🍊 Above the auto-approve limit the tool waits for the approval card and never bypasses it. */
    @Test
    void largeTradeWaitsForTheApprovalCardAndThenSettles() {
        ToolResult result = tradeTool.execute(context(analyst),
                new TradeExecuteTool.Args("YUZU", Trade.Side.BUY, 5));

        assertThat(result.status()).isEqualTo(ToolResult.Status.WAITING);
        Trade pending = portfolios.trades(analyst.agentId(), 10).getFirst();
        assertThat(pending.status()).isEqualTo(Trade.Status.PENDING_APPROVAL);
        assertThat(portfolios.portfolio(analyst.agentId()).cash()).isEqualByComparingTo(new BigDecimal("10000.00"));

        CardView card = openApproval();
        assertThat(card.prompt()).contains("YUZU").contains("BUY");
        cards.answer(card.id(), alice.id(), List.of(optionNamed(card, "Approve")), null);

        await(() -> portfolios.trade(analyst.agentId(), pending.id()).status() == Trade.Status.EXECUTED);
        Portfolio after = portfolios.portfolio(analyst.agentId());
        assertThat(after.cash()).isLessThan(new BigDecimal("10000.00"));
        assertThat(after.quantityOf("YUZU")).isEqualByComparingTo(new BigDecimal("5"));
    }

    /** v0.0.27 🍊 Rejecting the card leaves the portfolio untouched. */
    @Test
    void rejectedApprovalLeavesThePortfolioUntouched() {
        tradeTool.execute(context(analyst), new TradeExecuteTool.Args("YUZU", Trade.Side.BUY, 5));
        Trade pending = portfolios.trades(analyst.agentId(), 10).getFirst();

        CardView card = openApproval();
        cards.answer(card.id(), alice.id(), List.of(optionNamed(card, "Reject")), null);

        await(() -> portfolios.trade(analyst.agentId(), pending.id()).status() == Trade.Status.REJECTED);
        assertThat(portfolios.portfolio(analyst.agentId()).cash()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    /** v0.0.27 🍊 A trade over the hard maximum is blocked in code; no approval card can lift it. */
    @Test
    void overLimitTradeIsBlockedInCode() {
        assertThatThrownBy(() -> tradeTool.execute(context(analyst),
                new TradeExecuteTool.Args("YUZU", Trade.Side.BUY, 20)))
                .isInstanceOf(PermissionDeniedException.class);

        assertThat(portfolios.trades(analyst.agentId(), 10)).singleElement()
                .extracting(Trade::status).isEqualTo(Trade.Status.BLOCKED);
        assertThat(cards.recent(roomId, 10)).noneMatch(card -> card.kind().equals("APPROVAL"));
        assertThat(portfolios.portfolio(analyst.agentId()).cash()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    /** v0.0.27 🍊 Every trading tool re-checks its permission even when invoked outside the dispatcher. */
    @Test
    void directInvocationStillRequiresTradePermissions() {
        assertThatThrownBy(() -> quoteTool.execute(context(researcher), new MarketQuoteTool.Args(null)))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> portfolioTool.execute(context(researcher), new PortfolioReadTool.Args(0)))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> tradeTool.execute(context(researcher),
                new TradeExecuteTool.Args("PEEL", Trade.Side.BUY, 1)))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(portfolios.trades(researcher.agentId(), 10)).isEmpty();
    }

    /** v0.0.27 🍊 The single open approval card of the room. */
    private CardView openApproval() {
        return cards.recent(roomId, 20).stream().filter(card -> card.kind().equals("APPROVAL"))
                .filter(card -> card.status().equals("OPEN")).findFirst().orElseThrow();
    }

    /** v0.0.27 🍊 Option id of a label. */
    private static String optionNamed(CardView card, String label) {
        return card.options().stream().filter(option -> option.label().equalsIgnoreCase(label))
                .findFirst().orElseThrow().id();
    }

    /** v0.0.27 🍊 Waits up to 8 seconds for an asynchronous card handler to finish its work. */
    private static void await(BooleanSupplier condition) {
        for (int attempt = 0; attempt < 400 && !condition.getAsBoolean(); attempt++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        assertThat(condition.getAsBoolean()).as("the card handler finished its work").isTrue();
    }

    /** v0.0.27 🍊 A direct tool context for one acting coworker. */
    private ToolContext context(AgentProfile profile) {
        AgentContext ctx = runtimes.require(profile.agentId()).context("trace-trade-tools", null, time);
        return new ToolContext(ctx, "batch-trade-tools",
                "call-" + ThreadLocalRandom.current().nextInt(1_000_000), 0, "test trade tool", 0,
                deps.reporter().start(profile.agentId(), "TOOL", "test", ctx.traceId(), null));
    }
}
