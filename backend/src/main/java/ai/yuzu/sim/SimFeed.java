package ai.yuzu.sim;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.Permission;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.sim.email.Email;
import ai.yuzu.sim.email.EmailView;
import ai.yuzu.sim.email.FakeMailbox;
import ai.yuzu.sim.market.FakePortfolio;
import ai.yuzu.sim.market.Portfolio;
import ai.yuzu.sim.market.PortfolioView;
import ai.yuzu.sim.market.Trade;
import ai.yuzu.sim.market.TradeView;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** v0.0.11 🍊 Read side of the simulated world for the UI: a room's (or one agent's) e-mails, trades and portfolios. */
@Service
public class SimFeed {

    /** v0.0.11 🍊 Default number of e-mails or trades returned by the REST endpoints. */
    public static final int DEFAULT_LIMIT = 100;

    /** v0.0.11 🍊 Largest number of e-mails or trades returned at once. */
    public static final int MAX_LIMIT = 500;

    private static final Comparator<Email> NEWEST_EMAIL_FIRST =
            Comparator.comparing(Email::createdAt).thenComparingLong(Email::seq).reversed();
    private static final Comparator<Trade> NEWEST_TRADE_FIRST =
            Comparator.comparing(Trade::createdAt).thenComparingLong(Trade::seq).reversed();

    private final AgentService agents;
    private final FakeMailbox mailbox;
    private final FakePortfolio portfolio;
    private final NaturalTime time;

    /** v0.0.11 🍊 Injects collaborators. */
    public SimFeed(AgentService agents, FakeMailbox mailbox, FakePortfolio portfolio, NaturalTime time) {
        this.agents = agents;
        this.mailbox = mailbox;
        this.portfolio = portfolio;
        this.time = time;
    }

    /** v0.0.11 🍊 Newest-first e-mails of one agent, or of every present agent of the room when agentId is null. */
    public List<EmailView> emails(String roomId, AgentId agentId, int limit) {
        int safe = clamp(limit);
        return agentIds(roomId, agentId).stream()
                .flatMap(id -> mailbox.emails(id, safe).stream())
                .sorted(NEWEST_EMAIL_FIRST).limit(safe)
                .map(email -> email.toView(time)).toList();
    }

    /** v0.0.11 🍊 Newest-first trades of one agent, or of every present agent of the room when agentId is null. */
    public List<TradeView> trades(String roomId, AgentId agentId, int limit) {
        int safe = clamp(limit);
        return agentIds(roomId, agentId).stream()
                .flatMap(id -> portfolio.trades(id, safe).stream())
                .sorted(NEWEST_TRADE_FIRST).limit(safe)
                .map(trade -> trade.toView(time)).toList();
    }

    /** v0.0.11 🍊 One agent's portfolio, or the room's trading agents plus anyone who already holds an account. */
    public List<PortfolioView> portfolios(String roomId, AgentId agentId) {
        if (agentId != null) {
            return List.of(portfolio.portfolio(agentId).toView());
        }
        return agents.list(roomId).stream()
                .map(this::roomPortfolio)
                .flatMap(Optional::stream)
                .map(Portfolio::toView)
                .toList();
    }

    /** v0.0.11 🍊 A present agent's account if it traded, or its $10,000 starting account if it may trade. */
    private Optional<Portfolio> roomPortfolio(AgentProfile profile) {
        Optional<Portfolio> stored = portfolio.findPortfolio(profile.agentId());
        if (stored.isPresent()) {
            return stored;
        }
        boolean trades = profile.scope().has(Permission.TRADE_VIEW) || profile.scope().has(Permission.TRADE_EXECUTE);
        return trades ? Optional.of(portfolio.portfolio(profile.agentId())) : Optional.empty();
    }

    /** v0.0.11 🍊 The requested agent, or every present agent of the room. */
    private List<AgentId> agentIds(String roomId, AgentId agentId) {
        return agentId != null ? List.of(agentId) : agents.list(roomId).stream().map(AgentProfile::agentId).toList();
    }

    /** v0.0.11 🍊 Limits a requested page size to 1..500. */
    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
