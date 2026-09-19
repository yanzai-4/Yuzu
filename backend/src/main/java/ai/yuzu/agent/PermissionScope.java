package ai.yuzu.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * v0.0.6 🍊 Immutable permission set + limits of one agent; the single source of truth for code-level guards.
 *
 * <p>{@link #hash()} is a stable content hash used as a cache key (tool catalogs, safety verdicts), so a
 * permission change automatically selects fresh cache entries.</p>
 */
public final class PermissionScope {

    private final EnumSet<Permission> set;
    private final Set<Permission> permissions;
    private final Limits limits;
    private final String hash;

    /** v0.0.6 🍊 Creates a scope from permissions and limits. */
    public PermissionScope(Collection<Permission> permissions, Limits limits) {
        EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
        if (permissions != null) {
            set.addAll(permissions);
        }
        this.set = set;
        this.permissions = Collections.unmodifiableSet(set);
        this.limits = limits == null ? Limits.defaults() : limits;
        this.hash = computeHash(set, this.limits);
    }

    /** v0.0.6 🍊 True when the agent holds the permission. */
    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    /** v0.0.6 🍊 The permissions (ordered by enum declaration). */
    public Set<Permission> permissions() {
        return permissions;
    }

    /** v0.0.6 🍊 The limits. */
    public Limits limits() {
        return limits;
    }

    /** v0.0.6 🍊 Stable 16-hex content hash of permissions + limits. */
    public String hash() {
        return hash;
    }

    /** v0.0.6 🍊 Permission names for JSON storage and the API. */
    public List<String> names() {
        return permissions.stream().map(Enum::name).toList();
    }

    /** v0.0.6 🍊 Deterministic English description for prompts ("You MAY: ... You may NOT: ..."). */
    public String describe() {
        String allowed = permissions.stream().map(p -> "- " + p.name() + ": " + p.description())
                .collect(Collectors.joining("\n"));
        String denied = EnumSet.complementOf(set).stream().map(Permission::name).collect(Collectors.joining(", "));
        return "Allowed:\n" + (allowed.isEmpty() ? "- (nothing)" : allowed)
                + "\nNot allowed: " + (denied.isEmpty() ? "(none)" : denied)
                + "\nLimits: e-mail domains " + (limits.emailAllowedDomains().isEmpty() ? "(none)"
                : String.join(", ", limits.emailAllowedDomains()))
                + "; max " + limits.emailMaxPerHour() + " e-mails/hour"
                + "; max trade $" + fmt(limits.tradeMaxNotionalUsd())
                + " (human approval above $" + fmt(limits.tradeAutoApproveUsd()) + ")"
                + "; workspace quota " + limits.fileQuotaMb() + " MB";
    }

    /** v0.0.6 🍊 Formats dollar amounts without trailing zeros. */
    private static String fmt(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.2f", value);
    }

    /** v0.0.6 🍊 SHA-256 over a canonical string, truncated to 16 hex chars. */
    private static String computeHash(Set<Permission> set, Limits limits) {
        String canonical = set.stream().map(Enum::name).collect(Collectors.joining(",")) + "|"
                + String.join(",", limits.emailAllowedDomains()) + "|" + limits.emailMaxPerHour() + "|"
                + limits.tradeMaxNotionalUsd() + "|" + limits.tradeAutoApproveUsd() + "|" + limits.fileQuotaMb();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
