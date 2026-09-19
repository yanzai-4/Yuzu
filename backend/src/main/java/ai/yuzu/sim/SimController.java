package ai.yuzu.sim;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.sim.email.EmailView;
import ai.yuzu.sim.market.PortfolioView;
import ai.yuzu.sim.market.TradeView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** v0.0.11 🍊 REST view of the simulated world: {@code /api/sim/emails}, {@code /trades}, {@code /portfolios}. */
@RestController
@RequestMapping("/api/sim")
public class SimController {

    private final SimFeed feed;

    /** v0.0.11 🍊 Injects the read-side feed. */
    public SimController(SimFeed feed) {
        this.feed = feed;
    }

    /** v0.0.11 🍊 Newest-first simulated e-mails of the room's agents, or of one agent with {@code ?agentId=}. */
    @GetMapping("/emails")
    public List<EmailView> emails(@RequestParam(defaultValue = "room-0001") String roomId,
                                  @RequestParam(required = false) String agentId,
                                  @RequestParam(defaultValue = "" + SimFeed.DEFAULT_LIMIT) int limit) {
        return feed.emails(roomId, parseAgent(agentId), limit);
    }

    /** v0.0.11 🍊 Newest-first simulated trades of the room's agents, or of one agent with {@code ?agentId=}. */
    @GetMapping("/trades")
    public List<TradeView> trades(@RequestParam(defaultValue = "room-0001") String roomId,
                                  @RequestParam(required = false) String agentId,
                                  @RequestParam(defaultValue = "" + SimFeed.DEFAULT_LIMIT) int limit) {
        return feed.trades(roomId, parseAgent(agentId), limit);
    }

    /** v0.0.11 🍊 Simulated portfolios of the room's trading agents, or of one agent with {@code ?agentId=}. */
    @GetMapping("/portfolios")
    public List<PortfolioView> portfolios(@RequestParam(defaultValue = "room-0001") String roomId,
                                          @RequestParam(required = false) String agentId) {
        return feed.portfolios(roomId, parseAgent(agentId));
    }

    /** v0.0.11 🍊 Optional agent filter; a malformed id becomes BAD_REQUEST through IllegalArgumentException. */
    private static AgentId parseAgent(String agentId) {
        return agentId == null || agentId.isBlank() ? null : AgentId.of(agentId.strip());
    }
}
