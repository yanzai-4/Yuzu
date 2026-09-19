package ai.yuzu.sim.market;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.Permission;
import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.ConflictException;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** v0.0.11 🍊 Simulated brokerage account per agent: execute, record, approve and reject trades; sim.* events. */
@Service
public class FakePortfolio {

    /** v0.0.11 🍊 Reason stored on a trade executed after a human approval. */
    public static final String APPROVED_REASON = "Approved by a human reviewer.";

    /** v0.0.11 🍊 Default reason stored when a human rejects a pending trade. */
    public static final String REJECTED_REASON = "Rejected by a human reviewer.";

    private static final int MAX_REASON_CHARS = 2_000;
    private static final int MAX_CARD_ID_CHARS = 32;
    private static final int MAX_LIST_LIMIT = 500;

    private final PortfolioRepository portfolios;
    private final TradeRepository trades;
    private final PortfolioLedger ledger;
    private final FakeBroker broker;
    private final AgentService agents;
    private final SseHub hub;
    private final NaturalTime time;
    private final Map<AgentId, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** v0.0.11 🍊 Injects collaborators. */
    public FakePortfolio(PortfolioRepository portfolios, TradeRepository trades, PortfolioLedger ledger,
                         FakeBroker broker, AgentService agents, SseHub hub, NaturalTime time) {
        this.portfolios = portfolios;
        this.trades = trades;
        this.ledger = ledger;
        this.broker = broker;
        this.agents = agents;
        this.hub = hub;
        this.time = time;
    }

    /** v0.0.11 🍊 The agent's portfolio; an agent that never traded has $10,000 cash (nothing is written on read). */
    public Portfolio portfolio(AgentId agentId) {
        return portfolios.find(agentId).orElseGet(() -> Portfolio.starting(agentId, time.nowInstant()));
    }

    /** v0.0.11 🍊 The stored portfolio, or empty when the agent never traded. */
    public Optional<Portfolio> findPortfolio(AgentId agentId) {
        return portfolios.find(agentId);
    }

    /** v0.0.23 🍊 Executes at the current price; hard limit → BLOCKED, above auto-approve → PENDING_APPROVAL, no cash/shares → REJECTED. */
    public Trade executeTrade(AgentId agentId, String symbol, Trade.Side side, BigDecimal qty) {
        Order order = Order.of(symbol, side, qty);
        PortfolioLedger.Outcome outcome;
        String blockedReason;
        boolean awaitingApproval = false;
        ReentrantLock lock = lockFor(agentId);
        lock.lock();
        try {
            Instant now = time.nowInstant();
            Quote quote = broker.quote(order.symbol());
            BigDecimal notional = quote.notional(order.qty());
            blockedReason = hardLimitViolation(agentId, notional).orElse(null);
            if (blockedReason != null) {
                outcome = new PortfolioLedger.Outcome(trades.insert(Trade.draft(agentId, order, quote.price(),
                        notional, Trade.Status.BLOCKED, null, blockedReason, now)), null);
            } else {
                TradeRules.Decision decision = tradeDecision(agentId, notional);
                awaitingApproval = decision.needsApproval();
                outcome = awaitingApproval
                        ? new PortfolioLedger.Outcome(trades.insert(Trade.draft(agentId, order, quote.price(),
                        notional, Trade.Status.PENDING_APPROVAL, null, decision.reason(), now)), null)
                        : ledger.execute(agentId, order, quote.price(), notional, now);
            }
        } finally {
            lock.unlock();
        }
        publish(outcome);
        if (blockedReason != null) {
            throw blocked(outcome.trade(), blockedReason);
        }
        if (awaitingApproval) {
            return outcome.trade();
        }
        if (!outcome.executed()) {
            throw rejected(outcome.trade());
        }
        return outcome.trade();
    }

    /** v0.0.11 🍊 Records a trade that did not execute (PENDING_APPROVAL, REJECTED or BLOCKED) at the current price. */
    public Trade recordTrade(AgentId agentId, String symbol, Trade.Side side, BigDecimal qty, Trade.Status status,
                             String reason, String cardId) {
        if (status == null || status == Trade.Status.EXECUTED) {
            throw new BadRequestException("recordTrade stores PENDING_APPROVAL, REJECTED or BLOCKED trades; "
                    + "use executeTrade to execute one.");
        }
        String card = cardId == null || cardId.isBlank() ? null : cardId.strip();
        if (card != null && card.length() > MAX_CARD_ID_CHARS) {
            throw new BadRequestException("A card id can have at most " + MAX_CARD_ID_CHARS + " characters.");
        }
        Order order = Order.of(symbol, side, qty);
        Quote quote = broker.quote(order.symbol());
        Trade trade = trades.insert(Trade.draft(agentId, order, quote.price(), quote.notional(order.qty()), status,
                card, cleanReason(reason), time.nowInstant()));
        publish(trade);
        return trade;
    }

    /** v0.0.11 🍊 Executes an approved PENDING_APPROVAL trade at the current price; hard limits still apply (BLOCKED). */
    public Trade approvePending(String tradeId) {
        AgentId owner = ownerOf(tradeId);
        PortfolioLedger.Outcome outcome;
        String blockedReason;
        ReentrantLock lock = lockFor(owner);
        lock.lock();
        try {
            Trade pending = requirePending(owner, tradeId);
            Instant now = time.nowInstant();
            Quote quote = broker.quote(pending.symbol());
            BigDecimal notional = quote.notional(pending.qty());
            blockedReason = hardLimitViolation(owner, notional).orElse(null);
            outcome = blockedReason != null
                    ? new PortfolioLedger.Outcome(ledger.decide(pending, Trade.Status.BLOCKED, quote.price(), notional,
                    blockedReason, now), null)
                    : ledger.settle(pending, quote.price(), notional, APPROVED_REASON, now);
        } finally {
            lock.unlock();
        }
        publish(outcome);
        if (blockedReason != null) {
            throw blocked(outcome.trade(), blockedReason);
        }
        if (!outcome.executed()) {
            throw rejected(outcome.trade());
        }
        return outcome.trade();
    }

    /** v0.0.11 🍊 Marks a PENDING_APPROVAL trade REJECTED with the default reason. */
    public Trade rejectPending(String tradeId) {
        return rejectPending(tradeId, null);
    }

    /** v0.0.11 🍊 Marks a PENDING_APPROVAL trade REJECTED (CONFLICT when it is no longer pending). */
    public Trade rejectPending(String tradeId, String reason) {
        AgentId owner = ownerOf(tradeId);
        Trade rejected;
        ReentrantLock lock = lockFor(owner);
        lock.lock();
        try {
            Trade pending = requirePending(owner, tradeId);
            String why = reason == null || reason.isBlank() ? REJECTED_REASON : cleanReason(reason);
            rejected = ledger.decide(pending, Trade.Status.REJECTED, pending.price(), pending.notional(), why,
                    time.nowInstant());
        } finally {
            lock.unlock();
        }
        publish(rejected);
        return rejected;
    }

    /** v0.0.11 🍊 One trade of the agent (NOT_FOUND for unknown ids and other agents' trades). */
    public Trade trade(AgentId agentId, String tradeId) {
        return trades.find(agentId, tradeId).orElseThrow(() -> notFound(tradeId));
    }

    /** v0.0.11 🍊 The agent's latest trades, newest first. */
    public List<Trade> trades(AgentId agentId, int limit) {
        return trades.recent(agentId, Math.max(1, Math.min(limit, MAX_LIST_LIMIT)));
    }

    /** v0.0.11 🍊 Owner of a trade id: record ids embed the agent hex ({@code trade-<hex>-<10hex>}). */
    static AgentId ownerOf(String tradeId) {
        String prefix = DataName.TRADE.prefix() + "-";
        if (tradeId == null || !IdGen.isRecordId(tradeId) || !tradeId.startsWith(prefix)) {
            throw notFound(tradeId);
        }
        return AgentId.of("agent-" + tradeId.substring(prefix.length(), prefix.length() + 4));
    }

    /** v0.0.11 🍊 Limits that no approval can lift: active agent, TRADE_EXECUTE permission, max notional. */
    private Optional<String> hardLimitViolation(AgentId agentId, BigDecimal notional) {
        Optional<AgentProfile> profile = agents.find(agentId);
        if (profile.isEmpty() || !profile.get().isPresent()) {
            return Optional.of("Only active coworkers can trade.");
        }
        if (!profile.get().scope().has(Permission.TRADE_EXECUTE)) {
            return Optional.of("This agent does not have the TRADE_EXECUTE permission.");
        }
        TradeRules.Decision decision = TradeRules.decide(profile.get().scope().limits(), notional);
        return decision.blocked() ? Optional.of(decision.reason()) : Optional.empty();
    }

    /** v0.0.11 🍊 The active trader's numeric verdict; callers first reject absent or unauthorized agents. */
    private TradeRules.Decision tradeDecision(AgentId agentId, BigDecimal notional) {
        return agents.find(agentId).filter(AgentProfile::isPresent).filter(profile ->
                profile.scope().has(Permission.TRADE_EXECUTE)).map(profile ->
                TradeRules.decide(profile.scope().limits(), notional)).orElseThrow(() ->
                new IllegalStateException("A permitted trader disappeared while its trade was being checked."));
    }

    /** v0.0.11 🍊 The trade if it is still waiting for a decision; NOT_FOUND or CONFLICT otherwise. */
    private Trade requirePending(AgentId owner, String tradeId) {
        Trade trade = trades.find(owner, tradeId).orElseThrow(() -> notFound(tradeId));
        if (trade.status() != Trade.Status.PENDING_APPROVAL) {
            throw (ConflictException) new ConflictException("Trade " + tradeId + " is already " + trade.status() + ".")
                    .with("tradeId", tradeId).with("status", trade.status().name()).forAgent(owner.value());
        }
        return trade;
    }

    /** v0.0.11 🍊 Publishes the trade and, when it changed, the portfolio. */
    private void publish(PortfolioLedger.Outcome outcome) {
        publish(outcome.trade());
        if (outcome.portfolio() != null) {
            publish(outcome.portfolio());
        }
    }

    /** v0.0.11 🍊 Publishes sim.trade to the agent's room. */
    private void publish(Trade trade) {
        publish(trade.agentId(), EventType.SIM_TRADE, trade.toView(time));
    }

    /** v0.0.11 🍊 Publishes sim.portfolio to the agent's room. */
    private void publish(Portfolio portfolio) {
        publish(portfolio.agentId(), EventType.SIM_PORTFOLIO, portfolio.toView());
    }

    /** v0.0.11 🍊 Sends an event to the agent's room (every room when the agent is unknown). */
    private void publish(AgentId agentId, EventType type, Object view) {
        Optional<String> roomId = agents.find(agentId).map(AgentProfile::roomId);
        if (roomId.isPresent()) {
            hub.publish(roomId.get(), type, agentId.value(), view);
        } else {
            hub.publishAll(type, agentId.value(), view);
        }
    }

    /** v0.0.11 🍊 PERMISSION_DENIED for a BLOCKED trade (details: trade id, reason, notional). */
    private static PermissionDeniedException blocked(Trade trade, String reason) {
        return (PermissionDeniedException) new PermissionDeniedException("Trade blocked: " + reason)
                .with("tradeId", trade.id()).with("reason", reason).with("notional", trade.notional())
                .forAgent(trade.agentId().value());
    }

    /** v0.0.11 🍊 CONFLICT for a REJECTED trade (missing cash or shares). */
    private static ConflictException rejected(Trade trade) {
        return (ConflictException) new ConflictException("Trade rejected: " + trade.reason())
                .with("tradeId", trade.id()).with("reason", trade.reason()).forAgent(trade.agentId().value());
    }

    /** v0.0.11 🍊 NOT_FOUND for a trade id (shown safely). */
    private static NotFoundException notFound(String tradeId) {
        String shown = String.valueOf(tradeId).replaceAll("\\p{Cntrl}", "?");
        shown = shown.length() > 40 ? shown.substring(0, 40) + "…" : shown;
        return (NotFoundException) new NotFoundException("Unknown trade " + shown + ".").with("tradeId", shown);
    }

    /** v0.0.11 🍊 Reason on one line, at most 2,000 characters (null stays null). */
    private static String cleanReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String clean = reason.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", " ").strip();
        return clean.length() <= MAX_REASON_CHARS ? clean : clean.substring(0, MAX_REASON_CHARS);
    }

    /** v0.0.11 🍊 The agent's trading lock (serializes quote, limit check and settlement per agent). */
    private ReentrantLock lockFor(AgentId agentId) {
        return locks.computeIfAbsent(agentId, id -> new ReentrantLock());
    }
}
