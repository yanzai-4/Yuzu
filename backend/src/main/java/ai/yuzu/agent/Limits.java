package ai.yuzu.agent;

import java.util.List;

/**
 * v0.0.6 🍊 Numeric and list limits attached to an agent's permissions (enforced in code).
 *
 * @param emailAllowedDomains recipient domains the agent may e-mail
 * @param emailMaxPerHour     maximum e-mails per rolling hour
 * @param tradeMaxNotionalUsd maximum notional of a single trade
 * @param tradeAutoApproveUsd trades above this need a human approval card
 * @param fileQuotaMb         workspace quota in megabytes
 */
public record Limits(List<String> emailAllowedDomains, int emailMaxPerHour, double tradeMaxNotionalUsd,
                     double tradeAutoApproveUsd, int fileQuotaMb) {

    /** v0.0.6 🍊 Normalizes nulls and negative numbers. */
    public Limits {
        emailAllowedDomains = emailAllowedDomains == null ? List.of()
                : emailAllowedDomains.stream().map(d -> d.trim().toLowerCase()).filter(d -> !d.isEmpty()).toList();
        emailMaxPerHour = Math.max(0, emailMaxPerHour);
        tradeMaxNotionalUsd = Math.max(0, tradeMaxNotionalUsd);
        tradeAutoApproveUsd = Math.max(0, Math.min(tradeAutoApproveUsd, tradeMaxNotionalUsd));
        fileQuotaMb = Math.max(1, fileQuotaMb);
    }

    /** v0.0.6 🍊 Conservative defaults (no e-mail domains, no trading). */
    public static Limits defaults() {
        return new Limits(List.of(), 10, 0, 0, 50);
    }

    /** v0.0.6 🍊 Returns a copy where every non-null field of the patch replaces the current value. */
    public Limits merge(LimitsPatch patch) {
        if (patch == null) {
            return this;
        }
        return new Limits(
                patch.emailAllowedDomains() != null ? patch.emailAllowedDomains() : emailAllowedDomains,
                patch.emailMaxPerHour() != null ? patch.emailMaxPerHour() : emailMaxPerHour,
                patch.tradeMaxNotionalUsd() != null ? patch.tradeMaxNotionalUsd() : tradeMaxNotionalUsd,
                patch.tradeAutoApproveUsd() != null ? patch.tradeAutoApproveUsd() : tradeAutoApproveUsd,
                patch.fileQuotaMb() != null ? patch.fileQuotaMb() : fileQuotaMb);
    }

    /** v0.0.6 🍊 Partial limits sent by the UI (null = unchanged). */
    public record LimitsPatch(List<String> emailAllowedDomains, Integer emailMaxPerHour, Double tradeMaxNotionalUsd,
                              Double tradeAutoApproveUsd, Integer fileQuotaMb) {
    }
}
