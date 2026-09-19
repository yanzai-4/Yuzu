package ai.yuzu.tool.impl.mail;

import ai.yuzu.agent.Permission;
import ai.yuzu.card.CardView;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.sim.email.Email;
import ai.yuzu.sim.email.EmailRules;
import ai.yuzu.sim.email.FakeMailbox;
import ai.yuzu.tool.spi.ApprovalGate;
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

/**
 * v0.0.27 🍊 Sends an e-mail to a customer — but never alone: the call opens an approval card and returns
 * {@code WAITING}.
 *
 * <p>The customer-liaison workflow is deliberate: outbound mail is high risk, so it passes the behavior review,
 * the high-risk second review, {@link PermissionGuard}, the {@link ApprovalGate} card and finally the mailbox's
 * own guards. Recipient allowlist, hourly rate limit, sender identity and the 10-minute duplicate window stay
 * in {@link FakeMailbox} / {@link EmailRules}; the tool only asks them, so an approval can never widen a limit.
 * Recipients the rules already refuse are recorded as a BLOCKED attempt without bothering a human.</p>
 */
@Component
public class EmailSendTool implements Tool<EmailSendTool.Args> {

    /** v0.0.27 🍊 Arguments. */
    public record Args(@Desc("Recipient addresses; every domain must be on my allowlist") List<String> to,
                       @Desc("Subject line (one line, at most 300 characters)") String subject,
                       @Desc("The complete e-mail body I want to send") String body) {
    }

    /** v0.0.27 🍊 Characters of the body shown on the approval card. */
    static final int CARD_BODY_CHARS = 2_000;

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("email_send",
            "Send an e-mail to a customer. It is NOT sent right away: a human sees an approval card first and "
                    + "the e-mail only leaves after they approve it.",
            Args.class, Set.of(Permission.EMAIL_SEND), Risk.HIGH, Duration.ofSeconds(30), true,
            "the mail system confirmed");

    private final FakeMailbox mailbox;
    private final ApprovalGate approvals;
    private final PermissionGuard guard;
    private final Jsons jsons;
    private final NaturalTime time;

    /** v0.0.27 🍊 Injects collaborators. */
    public EmailSendTool(FakeMailbox mailbox, ApprovalGate approvals, PermissionGuard guard, Jsons jsons,
                         NaturalTime time) {
        this.mailbox = mailbox;
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

    /** v0.0.27 🍊 Checks the mail rules, then opens the approval card and waits (nothing is sent here). */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.EMAIL_SEND);
        String subject = args.subject() == null ? "" : args.subject().strip();
        String body = args.body() == null ? "" : args.body().stripTrailing();
        if (subject.isEmpty() || body.isBlank()) {
            return ToolResult.error("An e-mail needs both a subject and a body.", time.nowInstant());
        }
        List<String> recipients = EmailRules.normalize(args.to());
        EmailRules.Result rules = EmailRules.check(ctx.profile().scope().limits(), recipients,
                mailbox.countSentSince(ctx.agent().agentId(), time.nowInstant().minus(FakeMailbox.RATE_WINDOW)));
        if (rules.blocked()) {
            Email attempt = mailbox.recordBlocked(ctx.agent().agentId(), null, recipients, subject, body);
            return ToolResult.denied("I did not send this e-mail (recorded as " + attempt.id() + "): "
                    + String.join(" ", rules.reasons()), time.nowInstant());
        }
        CardView card = approvals.request(ctx, EmailApprovalHandler.PURPOSE, prompt(rules.recipients(), subject, body),
                jsons.write(new EmailDraft(rules.recipients(), subject, body)));
        return ToolResult.waiting("I asked the humans to approve this e-mail to "
                + MailFormat.recipients(rules.recipients()) + " (approval card " + card.id()
                + "). It is not sent yet; I will hear about it when somebody decides.", time.nowInstant());
    }

    /** v0.0.27 🍊 What the human sees on the approval card: the full header line and a bounded body preview. */
    private static String prompt(List<String> recipients, String subject, String body) {
        return "Approve sending this e-mail?\nTo: " + String.join(", ", recipients) + "\nSubject: " + subject
                + "\n\n" + MailFormat.clip(body, CARD_BODY_CHARS);
    }
}
