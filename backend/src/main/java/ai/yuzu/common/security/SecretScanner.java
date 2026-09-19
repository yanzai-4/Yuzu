package ai.yuzu.common.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v0.0.16 🍊 Deterministic, code-level detection and redaction of credentials (runs before any model sees text).
 *
 * <p>Covers OpenAI-style keys, bearer tokens, AWS access keys, GitHub tokens, private-key blocks, JWTs and
 * "password=..." pairs. Used on inbound content (fast block without an AI call), tool results, and EVERY outbound
 * prompt (the LLM gateway redacts all messages), so no credential ever reaches a model provider.</p>
 */
public final class SecretScanner {

    /** v0.0.16 🍊 One detected secret. */
    public record Finding(String kind, int start, int end) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("API key", Pattern.compile("\\b(sk|rk|pk)-(?:proj-|live-|test-)?[A-Za-z0-9_\\-]{16,}")),
            new Rule("Bearer token", Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._\\-]{20,}")),
            new Rule("AWS access key", Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b")),
            new Rule("GitHub token", Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{30,}\\b")),
            new Rule("Private key", Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----")),
            new Rule("JWT", Pattern.compile("\\beyJ[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}")),
            new Rule("Password", Pattern.compile("(?i)\\b(password|passwd|pwd)\\s*[:=]\\s*\\S{4,}")));

    private SecretScanner() {
    }

    /** v0.0.16 🍊 All secrets found in a text (empty list when clean). */
    public static List<Finding> scan(String text) {
        List<Finding> findings = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return findings;
        }
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(text);
            while (m.find()) {
                findings.add(new Finding(rule.kind(), m.start(), m.end()));
            }
        }
        return findings;
    }

    /** v0.0.16 🍊 Replaces every secret with "[redacted <kind>]". */
    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (Rule rule : RULES) {
            result = rule.pattern().matcher(result).replaceAll("[redacted " + rule.kind().toLowerCase() + "]");
        }
        return result;
    }

    /** v0.0.16 🍊 A named detection pattern. */
    private record Rule(String kind, Pattern pattern) {
    }
}
