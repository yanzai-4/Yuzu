package ai.yuzu.tool.impl.trade;

import ai.yuzu.card.CardAnswer;
import ai.yuzu.card.CardAnswerHandler;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.internal.intake.NoticeService;
import ai.yuzu.sim.market.FakePortfolio;
import ai.yuzu.sim.market.Trade;
import ai.yuzu.tool.spi.ApprovalGate;
import org.springframework.stereotype.Component;

/**
 * v0.0.27 🍊 Finishes a {@code trade_execute} call once a human decided its approval card.
 *
 * <p>Approved → {@link FakePortfolio#approvePending} settles the parked trade at the price of that moment and
 * re-checks the hard limits, so a trade that grew past the maximum meanwhile is still BLOCKED. Rejected →
 * {@code rejectPending}. Both paths are idempotent in the ledger (the decision updates the row only while it is
 * still PENDING_APPROVAL), and the agent learns the outcome through a code-made notice.</p>
 */
@Component
public class TradeApprovalHandler implements CardAnswerHandler {

    /** v0.0.27 🍊 Card purpose of simulated-trade approvals. */
    public static final String PURPOSE = "TRADE_APPROVAL";

    private final FakePortfolio portfolios;
    private final NoticeService notices;
    private final Jsons jsons;
    private final NaturalTime time;

    /** v0.0.27 🍊 Injects collaborators. */
    public TradeApprovalHandler(FakePortfolio portfolios, NoticeService notices, Jsons jsons, NaturalTime time) {
        this.portfolios = portfolios;
        this.notices = notices;
        this.jsons = jsons;
        this.time = time;
    }

    /** v0.0.27 🍊 Purpose served. */
    @Override
    public String purpose() {
        return PURPOSE;
    }

    /** v0.0.27 🍊 Settles or cancels the parked trade and tells the agent what a human decided. */
    @Override
    public void onAnswer(CardAnswer answer) {
        if (answer.payload() == null || answer.payload().isBlank()) {
            return;
        }
        AgentId agentId = AgentId.of(answer.agentId());
        PendingTrade pending = jsons.read(answer.payload(), PendingTrade.class);
        String human = answer.answeredByName();
        try {
            if (!ApprovalGate.approved(answer)) {
                Trade refused = portfolios.rejectPending(pending.tradeId(), human + " rejected the trade.");
                notify(agentId, answer, human + " rejected my trade " + TradeFormat.order(refused)
                        + ", so nothing was bought or sold. I should ask what they would prefer.");
                return;
            }
            Trade done = portfolios.approvePending(pending.tradeId());
            notify(agentId, answer, human + " approved my trade; it settled as " + TradeFormat.order(done)
                    + " at " + time.compact(done.updatedAt()) + ".");
        } catch (PermissionDeniedException e) {
            notify(agentId, answer, human + " approved my trade, but it was still blocked: " + e.getMessage()
                    + " An approval cannot lift that limit.");
        } catch (YuzuException e) {
            notify(agentId, answer, human + " decided my trade " + pending.tradeId()
                    + ", but it could not be completed: " + e.getMessage());
        }
    }

    /** v0.0.27 🍊 Tells the agent what happened, attributed to the human who decided the card. */
    private void notify(AgentId agentId, CardAnswer answer, String text) {
        notices.notify(agentId, answer.answeredByName() + " (human)", text, answer.traceId(), 0);
    }
}
