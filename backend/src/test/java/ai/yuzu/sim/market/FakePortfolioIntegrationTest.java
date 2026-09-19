package ai.yuzu.sim.market;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Limits;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.UpdateAgentRequest;
import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.ConflictException;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.TestRooms;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static ai.yuzu.sim.market.Trade.Side.BUY;
import static ai.yuzu.sim.market.Trade.Side.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** v0.0.11 🍊 Portfolio against MySQL: execution math, no negative cash, no shorting, limits, approvals, concurrency. */
@IntegrationTest
class FakePortfolioIntegrationTest {

    private static final BigDecimal STARTING_CASH = new BigDecimal("10000.00");

    @Autowired
    private FakePortfolio portfolio;

    @Autowired
    private AgentService agents;

    @Autowired
    private SseHub hub;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ObjectMapper mapper;

    private String room;

    /** v0.0.11 🍊 Every test works in its own room. */
    @BeforeEach
    void setUp() {
        room = TestRooms.create(jdbc);
    }

    /** v0.0.11 🍊 An agent that never traded sees $10,000 and nothing is stored yet. */
    @Test
    void startingPortfolioHasTenThousandDollars() {
        AgentProfile analyst = analyst(50_000, 50_000);
        Portfolio start = portfolio.portfolio(analyst.agentId());
        assertThat(start.cash()).isEqualByComparingTo(STARTING_CASH);
        assertThat(start.positions()).isEmpty();
        assertThat(portfolio.findPortfolio(analyst.agentId())).isEmpty();
    }

    /** v0.0.11 🍊 Buying and selling move cash by the notional and keep quantities and the average price right. */
    @Test
    void buyAndSellUpdateCashAndPositions() {
        AgentProfile analyst = analyst(50_000, 50_000);
        Trade buy = portfolio.executeTrade(analyst.agentId(), "citr", BUY, new BigDecimal("10"));
        assertThat(buy.status()).isEqualTo(Trade.Status.EXECUTED);
        assertThat(buy.symbol()).isEqualTo("CITR");
        assertThat(buy.id()).matches("^trade-" + analyst.agentId().hex() + "-[0-9a-f]{10}$");
        assertThat(buy.notional()).isEqualByComparingTo(buy.price().multiply(BigDecimal.TEN)
                .setScale(2, RoundingMode.HALF_UP));

        Portfolio afterBuy = portfolio.portfolio(analyst.agentId());
        assertThat(afterBuy.cash()).isEqualByComparingTo(STARTING_CASH.subtract(buy.notional()));
        assertThat(afterBuy.quantityOf("CITR")).isEqualByComparingTo("10");
        assertThat(afterBuy.positions().get(0).avgPrice()).isEqualByComparingTo(buy.price());
        assertThat(afterBuy.version()).isEqualTo(1);

        Trade sell = portfolio.executeTrade(analyst.agentId(), "CITR", SELL, new BigDecimal("4"));
        Portfolio afterSell = portfolio.portfolio(analyst.agentId());
        assertThat(afterSell.cash()).isEqualByComparingTo(afterBuy.cash().add(sell.notional()));
        assertThat(afterSell.quantityOf("CITR")).isEqualByComparingTo("6");
        assertThat(afterSell.positions().get(0).avgPrice()).isEqualByComparingTo(buy.price());
        assertThat(afterSell.version()).isEqualTo(2);

        portfolio.executeTrade(analyst.agentId(), "CITR", SELL, new BigDecimal("6"));
        assertThat(portfolio.portfolio(analyst.agentId()).positions()).isEmpty();
        assertThat(portfolio.trades(analyst.agentId(), 10)).hasSize(3)
                .allSatisfy(trade -> assertThat(trade.status()).isEqualTo(Trade.Status.EXECUTED));
    }

    /** v0.0.11 🍊 A buy bigger than the cash is REJECTED (recorded, CONFLICT) and changes nothing. */
    @Test
    void cashNeverGoesNegative() {
        AgentProfile analyst = analyst(50_000, 50_000);
        ConflictException rejected = catchThrowableOfType(() -> portfolio.executeTrade(analyst.agentId(), "YUZU", BUY,
                new BigDecimal("100")), ConflictException.class);
        assertThat(rejected).hasMessageContaining("Insufficient cash");
        Trade trade = portfolio.trade(analyst.agentId(), (String) rejected.details().get("tradeId"));
        assertThat(trade.status()).isEqualTo(Trade.Status.REJECTED);
        assertThat(trade.reason()).startsWith("Insufficient cash");
        Portfolio after = portfolio.portfolio(analyst.agentId());
        assertThat(after.cash()).isEqualByComparingTo(STARTING_CASH);
        assertThat(after.positions()).isEmpty();
    }

    /** v0.0.11 🍊 Selling what is not held is short selling: REJECTED and recorded. */
    @Test
    void shortSellingIsRejected() {
        AgentProfile analyst = analyst(50_000, 50_000);
        assertThatThrownBy(() -> portfolio.executeTrade(analyst.agentId(), "LIME", SELL, BigDecimal.ONE))
                .isInstanceOf(ConflictException.class).hasMessageContaining("short selling");
        portfolio.executeTrade(analyst.agentId(), "LIME", BUY, new BigDecimal("2"));
        assertThatThrownBy(() -> portfolio.executeTrade(analyst.agentId(), "LIME", SELL, new BigDecimal("3")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("only 2 held");
        assertThat(portfolio.portfolio(analyst.agentId()).quantityOf("LIME")).isEqualByComparingTo("2");
        assertThat(portfolio.trades(analyst.agentId(), 10)).extracting(Trade::status)
                .containsExactly(Trade.Status.REJECTED, Trade.Status.EXECUTED, Trade.Status.REJECTED);
    }

    /** v0.0.11 🍊 Above the maximum notional, or without TRADE_EXECUTE, a trade is BLOCKED and denied. */
    @Test
    void hardLimitsBlockTrades() {
        AgentProfile analyst = defaultAnalyst();
        PermissionDeniedException tooBig = catchThrowableOfType(() -> portfolio.executeTrade(analyst.agentId(), "YUZU",
                BUY, new BigDecimal("20")), PermissionDeniedException.class);
        assertThat(tooBig).hasMessageContaining("exceeds the per-trade maximum of $1,000.00");
        assertThat(portfolio.trade(analyst.agentId(), (String) tooBig.details().get("tradeId")).status())
                .isEqualTo(Trade.Status.BLOCKED);
        assertThat(portfolio.findPortfolio(analyst.agentId())).isEmpty();

        AgentProfile manager = agents.create(room, new CreateAgentRequest(Role.PROJECT_MANAGER, null, null, null, null,
                null));
        assertThatThrownBy(() -> portfolio.executeTrade(manager.agentId(), "PEEL", BUY, BigDecimal.ONE))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("TRADE_EXECUTE");
        assertThat(portfolio.trades(manager.agentId(), 10)).singleElement()
                .satisfies(trade -> assertThat(trade.status()).isEqualTo(Trade.Status.BLOCKED));
    }

    /** v0.0.11 🍊 A PENDING_APPROVAL trade executes when approved, exactly once. */
    @Test
    void pendingTradeIsExecutedOnApproval() {
        AgentProfile analyst = defaultAnalyst();
        String cardId = "card-" + analyst.agentId().hex() + "-0123456789";
        Trade pending = portfolio.recordTrade(analyst.agentId(), "YUZU", BUY, new BigDecimal("3"),
                Trade.Status.PENDING_APPROVAL, "Above the auto-approve limit.", cardId);
        assertThat(pending.status()).isEqualTo(Trade.Status.PENDING_APPROVAL);
        assertThat(pending.cardId()).isEqualTo(cardId);
        assertThat(TradeRules.decide(analyst.scope().limits(), pending.notional()).needsApproval()).isTrue();

        Trade executed = portfolio.approvePending(pending.id());
        assertThat(executed.id()).isEqualTo(pending.id());
        assertThat(executed.status()).isEqualTo(Trade.Status.EXECUTED);
        assertThat(executed.reason()).isEqualTo(FakePortfolio.APPROVED_REASON);
        assertThat(portfolio.trade(analyst.agentId(), pending.id()).status()).isEqualTo(Trade.Status.EXECUTED);
        assertThat(portfolio.portfolio(analyst.agentId()).quantityOf("YUZU")).isEqualByComparingTo("3");

        assertThatThrownBy(() -> portfolio.approvePending(pending.id())).isInstanceOf(ConflictException.class)
                .hasMessageContaining("already EXECUTED");
        assertThatThrownBy(() -> portfolio.rejectPending(pending.id())).isInstanceOf(ConflictException.class);
        assertThat(portfolio.portfolio(analyst.agentId()).quantityOf("YUZU")).isEqualByComparingTo("3");
    }

    /** v0.0.11 🍊 A rejected pending trade never touches the portfolio and cannot be approved later. */
    @Test
    void pendingTradeCanBeRejected() {
        AgentProfile analyst = defaultAnalyst();
        Trade pending = portfolio.recordTrade(analyst.agentId(), "LIME", SELL, BigDecimal.ONE,
                Trade.Status.PENDING_APPROVAL, null, null);
        Trade rejected = portfolio.rejectPending(pending.id(), "Not now.");
        assertThat(rejected.status()).isEqualTo(Trade.Status.REJECTED);
        assertThat(rejected.reason()).isEqualTo("Not now.");
        assertThat(portfolio.findPortfolio(analyst.agentId())).isEmpty();
        assertThatThrownBy(() -> portfolio.approvePending(pending.id())).isInstanceOf(ConflictException.class)
                .hasMessageContaining("already REJECTED");

        Trade other = portfolio.recordTrade(analyst.agentId(), "LIME", BUY, BigDecimal.ONE,
                Trade.Status.PENDING_APPROVAL, null, null);
        assertThat(portfolio.rejectPending(other.id()).reason()).isEqualTo(FakePortfolio.REJECTED_REASON);
    }

    /** v0.0.11 🍊 An approval cannot lift hard limits that shrank after the request. */
    @Test
    void approvalStillRespectsHardLimits() {
        AgentProfile analyst = defaultAnalyst();
        Trade pending = portfolio.recordTrade(analyst.agentId(), "PEEL", BUY, new BigDecimal("10"),
                Trade.Status.PENDING_APPROVAL, null, null);
        agents.update(analyst.agentId(), new UpdateAgentRequest(null, null, null, null,
                new Limits.LimitsPatch(null, null, 0.0, 0.0, null)));
        assertThatThrownBy(() -> portfolio.approvePending(pending.id())).isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Trading is disabled");
        assertThat(portfolio.trade(analyst.agentId(), pending.id()).status()).isEqualTo(Trade.Status.BLOCKED);
        assertThat(portfolio.findPortfolio(analyst.agentId())).isEmpty();
    }

    /** v0.0.11 🍊 An approved trade that can no longer settle (no shares any more) becomes REJECTED. */
    @Test
    void approvalRejectsWhatCanNoLongerSettle() {
        AgentProfile analyst = analyst(50_000, 100);
        Trade pending = portfolio.recordTrade(analyst.agentId(), "ZEST", SELL, new BigDecimal("5"),
                Trade.Status.PENDING_APPROVAL, null, null);
        assertThatThrownBy(() -> portfolio.approvePending(pending.id())).isInstanceOf(ConflictException.class)
                .hasMessageContaining("short selling");
        assertThat(portfolio.trade(analyst.agentId(), pending.id()).status()).isEqualTo(Trade.Status.REJECTED);
    }

    /** v0.0.11 🍊 Unknown trades, symbols and bad quantities fail cleanly without rows. */
    @Test
    void unknownTradesAndBadInput() {
        AgentProfile analyst = analyst(50_000, 50_000);
        assertThatThrownBy(() -> portfolio.approvePending("trade-ffff-0123456789")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> portfolio.approvePending("email-ffff-0123456789")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> portfolio.approvePending("not a trade")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> portfolio.rejectPending(null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> portfolio.executeTrade(analyst.agentId(), "AAPL", BUY, BigDecimal.ONE))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> portfolio.executeTrade(analyst.agentId(), "CITR", BUY, BigDecimal.ZERO))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> portfolio.executeTrade(analyst.agentId(), "CITR", BUY, new BigDecimal("-1")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> portfolio.executeTrade(analyst.agentId(), "CITR", BUY, new BigDecimal("0.00001")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> portfolio.executeTrade(analyst.agentId(), "CITR", null, BigDecimal.ONE))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> portfolio.recordTrade(analyst.agentId(), "CITR", BUY, BigDecimal.ONE,
                Trade.Status.EXECUTED, null, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Trade.Side.parse("hold")).isInstanceOf(BadRequestException.class);
        assertThat(Trade.Side.parse(" sell ")).isEqualTo(SELL);
        assertThat(portfolio.trades(analyst.agentId(), 10)).isEmpty();
    }

    /** v0.0.11 🍊 Parallel buys by one agent never lose an update (lock + optimistic version). */
    @Test
    void concurrentTradesNeverLoseAnUpdate() throws Exception {
        AgentProfile analyst = analyst(50_000, 50_000);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Trade>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return portfolio.executeTrade(analyst.agentId(), "ZEST", BUY, BigDecimal.ONE);
                }));
            }
            start.countDown();
            BigDecimal spent = BigDecimal.ZERO;
            for (Future<Trade> future : futures) {
                spent = spent.add(future.get().notional());
            }
            Portfolio after = portfolio.portfolio(analyst.agentId());
            assertThat(after.quantityOf("ZEST")).isEqualByComparingTo("8");
            assertThat(after.cash()).isEqualByComparingTo(STARTING_CASH.subtract(spent));
            assertThat(after.version()).isEqualTo(threads);
        } finally {
            pool.shutdownNow();
        }
    }

    /** v0.0.11 🍊 sim.trade and sim.portfolio events reach the agent's room. */
    @Test
    void eventsArePublishedToTheAgentsRoom() {
        AgentProfile analyst = analyst(50_000, 50_000);
        long cursor = hub.currentCursor();
        portfolio.executeTrade(analyst.agentId(), "LIME", BUY, new BigDecimal("2"));
        assertThat(events(cursor, EventType.SIM_TRADE, analyst)).singleElement().satisfies(event -> {
            assertThat(event.path("data").path("status").asText()).isEqualTo("EXECUTED");
            assertThat(event.path("data").path("symbol").asText()).isEqualTo("LIME");
            assertThat(event.path("data").path("qty").isNumber()).isTrue();
        });
        assertThat(events(cursor, EventType.SIM_PORTFOLIO, analyst)).singleElement().satisfies(event -> {
            assertThat(event.path("data").path("cash").asDouble()).isLessThan(10_000);
            assertThat(event.path("data").path("positions").get(0).path("symbol").asText()).isEqualTo("LIME");
        });
    }

    /** v0.0.11 🍊 A finance analyst with custom notional limits. */
    private AgentProfile analyst(double maxNotional, double autoApprove) {
        return agents.create(room, new CreateAgentRequest(Role.FINANCE_ANALYST, null, null, null, null,
                new Limits.LimitsPatch(null, null, maxNotional, autoApprove, null)));
    }

    /** v0.0.11 🍊 A finance analyst with the preset limits (max $1,000, auto-approve $200). */
    private AgentProfile defaultAnalyst() {
        return agents.create(room, new CreateAgentRequest(Role.FINANCE_ANALYST, null, null, null, null, null));
    }

    /** v0.0.11 🍊 Events of a type published for the agent (in its room) after a cursor. */
    private List<JsonNode> events(long after, EventType type, AgentProfile agent) {
        return hub.bufferedEvents().stream()
                .filter(event -> event.id() > after && event.type() == type && event.roomId().equals(agent.roomId()))
                .map(event -> {
                    try {
                        return mapper.readTree(event.json());
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .filter(node -> node.path("agentId").asText().equals(agent.agentId().value()))
                .toList();
    }
}
