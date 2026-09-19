package ai.yuzu.sim.email;

import ai.yuzu.agent.Limits;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** v0.0.11 🍊 Pure e-mail guard rules (allowlisted domains, hourly limit, address syntax, sender) shared by every layer. */
public final class EmailRules {

    /** v0.0.11 🍊 Most recipients one e-mail may have. */
    public static final int MAX_RECIPIENTS = 10;

    /** v0.0.11 🍊 Longest accepted address (RFC 5321 path limit). */
    public static final int MAX_ADDRESS_CHARS = 254;

    private static final Pattern ADDRESS = Pattern.compile(
            "^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");
    private static final int MAX_SHOWN_CHARS = 80;

    /** v0.0.11 🍊 Machine-readable reason of a refused e-mail. */
    public enum Code {
        NO_RECIPIENTS, TOO_MANY_RECIPIENTS, INVALID_ADDRESS, DOMAIN_NOT_ALLOWED, HOURLY_LIMIT, SENDER_MISMATCH,
        MISSING_PERMISSION
    }

    /** v0.0.11 🍊 One broken rule: a code plus an English explanation for the agent and the UI. */
    public record Violation(Code code, String message) {
    }

    /** v0.0.11 🍊 Outcome of a check: the normalized recipients and every broken rule (empty = allowed). */
    public record Result(List<String> recipients, List<Violation> violations) {

        /** v0.0.11 🍊 Copies both lists so the result is immutable. */
        public Result {
            recipients = List.copyOf(recipients);
            violations = List.copyOf(violations);
        }

        /** v0.0.11 🍊 True when no rule is broken. */
        public boolean allowed() {
            return violations.isEmpty();
        }

        /** v0.0.11 🍊 True when at least one rule is broken. */
        public boolean blocked() {
            return !violations.isEmpty();
        }

        /** v0.0.11 🍊 The explanations of every broken rule. */
        public List<String> reasons() {
            return violations.stream().map(Violation::message).toList();
        }

        /** v0.0.11 🍊 True when a rule with this code is broken. */
        public boolean has(Code code) {
            return violations.stream().anyMatch(v -> v.code() == code);
        }
    }

    /** v0.0.11 🍊 Static rules only. */
    private EmailRules() {
    }

    /** v0.0.11 🍊 Checks recipients against the limits: syntax, count, exact allowlisted domain and hourly limit. */
    public static Result check(Limits limits, List<String> recipients, int sentLastHour) {
        Limits effective = limits == null ? Limits.defaults() : limits;
        List<String> normalized = normalize(recipients);
        List<Violation> violations = new ArrayList<>();
        if (normalized.isEmpty()) {
            violations.add(new Violation(Code.NO_RECIPIENTS, "The e-mail has no recipients."));
        }
        if (normalized.size() > MAX_RECIPIENTS) {
            violations.add(new Violation(Code.TOO_MANY_RECIPIENTS, "Too many recipients: " + normalized.size()
                    + " (at most " + MAX_RECIPIENTS + " per e-mail)."));
        }
        Set<String> allowed = allowedDomains(effective);
        Set<String> refused = new LinkedHashSet<>();
        for (String address : normalized) {
            if (!isValidAddress(address)) {
                violations.add(new Violation(Code.INVALID_ADDRESS,
                        "Invalid e-mail address: \"" + printable(address) + "\"."));
            } else if (!allowed.contains(domainOf(address))) {
                refused.add(domainOf(address));
            }
        }
        if (!refused.isEmpty()) {
            violations.add(new Violation(Code.DOMAIN_NOT_ALLOWED, allowed.isEmpty()
                    ? "This agent may not e-mail any domain; refused: " + String.join(", ", refused) + "."
                    : "Recipient domain not allowed: " + String.join(", ", refused) + " (allowed: "
                    + String.join(", ", allowed) + ")."));
        }
        int sent = Math.max(0, sentLastHour);
        int max = effective.emailMaxPerHour();
        if (max == 0) {
            violations.add(new Violation(Code.HOURLY_LIMIT, "E-mail sending is disabled for this agent (limit 0 per hour)."));
        } else if (sent >= max) {
            violations.add(new Violation(Code.HOURLY_LIMIT, "Hourly e-mail limit reached: " + sent + " of " + max
                    + " e-mails sent in the last hour."));
        }
        return new Result(normalized, violations);
    }

    /** v0.0.11 🍊 An agent may only send as itself: any other sender address is a spoofing attempt. */
    public static Optional<Violation> checkSender(String ownAddress, String requestedFrom) {
        String own = ownAddress == null ? "" : ownAddress.strip().toLowerCase(Locale.ROOT);
        String requested = requestedFrom == null ? "" : requestedFrom.strip().toLowerCase(Locale.ROOT);
        if (requested.isEmpty() || requested.equals(own)) {
            return Optional.empty();
        }
        return Optional.of(new Violation(Code.SENDER_MISMATCH, "Agents may only send from their own address ("
                + own + "), not as \"" + printable(requested) + "\"."));
    }

    /** v0.0.11 🍊 Trims, lowercases and de-duplicates recipients, also splitting "a@x.com, b@y.com" style entries. */
    public static List<String> normalize(Collection<String> recipients) {
        Set<String> unique = new LinkedHashSet<>();
        if (recipients != null) {
            for (String entry : recipients) {
                if (entry == null) {
                    continue;
                }
                for (String part : entry.split("[,;]")) {
                    String address = part.strip().toLowerCase(Locale.ROOT);
                    if (!address.isEmpty()) {
                        unique.add(address);
                    }
                }
            }
        }
        return List.copyOf(unique);
    }

    /** v0.0.11 🍊 True for a plain ASCII address (no display names, IDN look-alikes, spaces or control characters). */
    public static boolean isValidAddress(String address) {
        return address != null && address.length() <= MAX_ADDRESS_CHARS && ADDRESS.matcher(address).matches();
    }

    /** v0.0.11 🍊 Lowercase domain after the last '@' ("" when there is none). */
    public static String domainOf(String address) {
        int at = address == null ? -1 : address.lastIndexOf('@');
        return at < 0 ? "" : address.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    /** v0.0.11 🍊 Allowed domains of the limits, lowercased and without a leading '@'. */
    private static Set<String> allowedDomains(Limits limits) {
        Set<String> allowed = new LinkedHashSet<>();
        for (String domain : limits.emailAllowedDomains()) {
            String clean = domain.strip().toLowerCase(Locale.ROOT);
            clean = clean.startsWith("@") ? clean.substring(1) : clean;
            if (!clean.isEmpty()) {
                allowed.add(clean);
            }
        }
        return allowed;
    }

    /** v0.0.11 🍊 Escapes control characters and shortens a value so it is safe inside an explanation. */
    static String printable(String value) {
        StringBuilder shown = new StringBuilder();
        value.codePoints().limit(MAX_SHOWN_CHARS).forEach(cp -> {
            if (Character.isISOControl(cp)) {
                shown.append(String.format("\\u%04X", cp));
            } else {
                shown.appendCodePoint(cp);
            }
        });
        return value.codePointCount(0, value.length()) > MAX_SHOWN_CHARS ? shown + "…" : shown.toString();
    }
}
