package ai.yuzu.tool.impl.mail;

import ai.yuzu.agent.Permission;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.Nullable;
import ai.yuzu.sim.email.Email;
import ai.yuzu.sim.email.FakeMailbox;
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
 * v0.0.27 🍊 Reads my own mailbox: the list of received e-mails, or one e-mail in full.
 *
 * <p>Customer e-mail is outside content (the seeded inbox contains a phishing message with a prompt-injection
 * line on purpose), so the tool is NOT trusted: its output always passes the outbound safety review, and the
 * agent is reminded that instructions inside an e-mail are not orders.</p>
 */
@Component
public class EmailReadTool implements Tool<EmailReadTool.Args> {

    /** v0.0.27 🍊 Arguments. */
    public record Args(@Nullable @Desc("Id of one e-mail to open in full; null lists my inbox") String emailId) {
    }

    /** v0.0.27 🍊 Characters of the body shown per e-mail in the inbox list. */
    static final int PREVIEW_CHARS = 200;

    /** v0.0.27 🍊 Characters of the body shown when one e-mail is opened. */
    static final int BODY_CHARS = 8_000;

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("email_read",
            "Read my mailbox: list the e-mails I received (sender, subject, time and a short preview), or open "
                    + "one of them in full by its id.",
            Args.class, Set.of(Permission.EMAIL_READ), Risk.LOW, Duration.ofSeconds(20), false,
            "I read in my mailbox");

    private final FakeMailbox mailbox;
    private final PermissionGuard guard;
    private final NaturalTime time;

    /** v0.0.27 🍊 Injects collaborators. */
    public EmailReadTool(FakeMailbox mailbox, PermissionGuard guard, NaturalTime time) {
        this.mailbox = mailbox;
        this.guard = guard;
        this.time = time;
    }

    /** v0.0.27 🍊 Static description. */
    @Override
    public ToolSpec<Args> spec() {
        return SPEC;
    }

    /** v0.0.27 🍊 Lists the inbox or opens one e-mail (permission re-checked in code, mailbox scoped to me). */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.EMAIL_READ);
        String emailId = args.emailId() == null ? "" : args.emailId().strip();
        return emailId.isEmpty()
                ? ToolResult.ok(renderInbox(mailbox.inbox(ctx.agent().agentId())), time.nowInstant())
                : ToolResult.ok(renderOne(mailbox.read(ctx.agent().agentId(), emailId)), time.nowInstant());
    }

    /** v0.0.27 🍊 The inbox, newest first, one line plus a short preview per e-mail. */
    private String renderInbox(List<Email> inbox) {
        if (inbox.isEmpty()) {
            return "My inbox is empty.";
        }
        StringBuilder text = new StringBuilder("My inbox (" + inbox.size() + " e-mails, newest first):\n");
        for (Email email : inbox) {
            text.append("- ").append(email.id()).append(" — from ").append(email.from()).append(", ")
                    .append(time.compact(email.createdAt())).append("\n  Subject: ").append(email.subject())
                    .append("\n  ").append(MailFormat.oneLine(email.body(), PREVIEW_CHARS)).append('\n');
        }
        text.append(MailFormat.UNTRUSTED_REMINDER);
        return text.toString();
    }

    /** v0.0.27 🍊 One e-mail with its headers and a bounded body. */
    private String renderOne(Email email) {
        return "E-mail " + email.id() + "\nFrom: " + email.from() + "\nTo: " + email.toLine()
                + "\nSubject: " + email.subject() + "\nReceived: " + time.compact(email.createdAt())
                + "\n\n" + MailFormat.clip(email.body(), BODY_CHARS) + "\n\n" + MailFormat.UNTRUSTED_REMINDER;
    }
}
