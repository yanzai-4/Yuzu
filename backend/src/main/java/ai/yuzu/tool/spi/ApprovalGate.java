package ai.yuzu.tool.spi;

import ai.yuzu.card.CardAnswer;
import ai.yuzu.card.CardService;
import ai.yuzu.card.CardView;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * v0.0.27 🍊 The human approval gate: layer 4 of the high-risk chain (behavior review → high-risk review →
 * {@link PermissionGuard} → approval card → the tool's own check).
 *
 * <p>A tool that must not act alone opens an Approve / Reject card here and returns {@code WAITING}. The rest of
 * the batch is never held back; the {@link ai.yuzu.card.CardAnswerHandler} registered for the purpose finishes
 * (or abandons) the work once a human decides. The gate never decides anything itself, so a tool can neither
 * skip it nor pre-approve its own call.</p>
 */
@Component
public class ApprovalGate {

    /** v0.0.27 🍊 Label of the approving option (the only answer that lets the work continue). */
    public static final String APPROVE = "Approve";

    /** v0.0.27 🍊 Label of the refusing option. */
    public static final String REJECT = "Reject";

    /** v0.0.27 🍊 Longest prompt an approval card shows (the tools cut their previews to fit). */
    public static final int MAX_PROMPT_CHARS = 4_000;

    private final CardService cards;

    /** v0.0.27 🍊 Injects the card service. */
    public ApprovalGate(CardService cards) {
        this.cards = cards;
    }

    /**
     * v0.0.27 🍊 Opens an Approve / Reject card for one tool call and returns it.
     *
     * @param purpose the {@link ai.yuzu.card.CardAnswerHandler} purpose that completes the work
     * @param payload JSON the handler needs to finish the work (a pending trade id, an e-mail draft, ...)
     */
    public CardView request(ToolContext ctx, String purpose, String prompt, String payload) {
        return cards.open(ctx.agent(), true, cut(prompt), List.of(APPROVE, REJECT), false, purpose, payload,
                ctx.batchId(), ctx.toolCallId());
    }

    /** v0.0.27 🍊 True only when a human picked "Approve"; anything else (including no option) refuses the work. */
    public static boolean approved(CardAnswer answer) {
        return answer != null && answer.labels() != null
                && answer.labels().stream().anyMatch(label -> label != null && APPROVE.equalsIgnoreCase(label.strip()));
    }

    /** v0.0.27 🍊 Cuts a card prompt to the maximum length with a visible marker. */
    private static String cut(String prompt) {
        String text = prompt == null ? "" : prompt.strip();
        return text.length() <= MAX_PROMPT_CHARS ? text : text.substring(0, MAX_PROMPT_CHARS) + "\n(…cut)";
    }
}
