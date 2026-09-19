package ai.yuzu.tool.impl.mail;

import ai.yuzu.card.CardAnswer;
import ai.yuzu.card.CardAnswerHandler;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.internal.intake.NoticeService;
import ai.yuzu.sim.email.Email;
import ai.yuzu.sim.email.FakeMailbox;
import ai.yuzu.tool.spi.ApprovalGate;
import org.springframework.stereotype.Component;

/**
 * v0.0.27 🍊 Finishes an {@code email_send} call once a human decided its approval card.
 *
 * <p>Approved → {@link FakeMailbox#send} runs every guard again (permission, own sender address, allowlist,
 * hourly limit, 10-minute duplicate window), so the approval only lifts the "a human must look at this" gate,
 * never a limit. Rejected → the attempt is recorded as BLOCKED so the Simulation tab shows it. Either way the
 * agent learns the outcome through a code-made notice, not as its own thought.</p>
 */
@Component
public class EmailApprovalHandler implements CardAnswerHandler {

    /** v0.0.27 🍊 Card purpose of outbound e-mail approvals. */
    public static final String PURPOSE = "EMAIL_APPROVAL";

    private final FakeMailbox mailbox;
    private final NoticeService notices;
    private final Jsons jsons;
    private final NaturalTime time;

    /** v0.0.27 🍊 Injects collaborators. */
    public EmailApprovalHandler(FakeMailbox mailbox, NoticeService notices, Jsons jsons, NaturalTime time) {
        this.mailbox = mailbox;
        this.notices = notices;
        this.jsons = jsons;
        this.time = time;
    }

    /** v0.0.27 🍊 Purpose served. */
    @Override
    public String purpose() {
        return PURPOSE;
    }

    /** v0.0.27 🍊 Sends the stored draft when the human approved, records a BLOCKED attempt when they did not. */
    @Override
    public void onAnswer(CardAnswer answer) {
        if (answer.payload() == null || answer.payload().isBlank()) {
            return;
        }
        AgentId agentId = AgentId.of(answer.agentId());
        EmailDraft draft = jsons.read(answer.payload(), EmailDraft.class);
        String human = answer.answeredByName();
        if (!ApprovalGate.approved(answer)) {
            Email refused = mailbox.recordBlocked(agentId, null, draft.to(), draft.subject(), draft.body());
            notify(agentId, answer, human + " rejected my e-mail \"" + draft.subject() + "\" to "
                    + MailFormat.recipients(draft.to()) + ", so it was not sent (recorded as " + refused.id()
                    + "). I should ask what to change before trying again.");
            return;
        }
        try {
            Email sent = mailbox.send(agentId, null, draft.to(), draft.subject(), draft.body());
            notify(agentId, answer, human + " approved my e-mail \"" + draft.subject() + "\"; it went out to "
                    + MailFormat.recipients(sent.to()) + " at " + time.compact(sent.createdAt()) + ".");
        } catch (PermissionDeniedException e) {
            notify(agentId, answer, human + " approved my e-mail \"" + draft.subject()
                    + "\", but the mail server still refused it: " + e.getMessage()
                    + " An approval cannot lift that limit.");
        } catch (YuzuException e) {
            notify(agentId, answer, human + " approved my e-mail \"" + draft.subject()
                    + "\", but it could not be sent: " + e.getMessage());
        }
    }

    /** v0.0.27 🍊 Tells the agent what happened, attributed to the human who decided the card. */
    private void notify(AgentId agentId, CardAnswer answer, String text) {
        notices.notify(agentId, answer.answeredByName() + " (human)", text, answer.traceId(), 0);
    }
}
