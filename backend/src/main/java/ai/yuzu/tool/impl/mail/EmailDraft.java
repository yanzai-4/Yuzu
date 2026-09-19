package ai.yuzu.tool.impl.mail;

import java.util.List;

/** v0.0.27 🍊 The e-mail an approval card is waiting on, stored as the card's JSON payload. */
public record EmailDraft(List<String> to, String subject, String body) {

    /** v0.0.27 🍊 Copies the recipients so the draft is immutable. */
    public EmailDraft {
        to = to == null ? List.of() : List.copyOf(to);
    }
}
