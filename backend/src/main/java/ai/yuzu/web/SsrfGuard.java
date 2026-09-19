package ai.yuzu.web;

import ai.yuzu.common.error.SandboxViolationException;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * v0.0.26 🍊 Server-side request forgery guard: the only door between an agent and an outside address.
 *
 * <p>Two levels. {@link #checkSyntax} is lexical and needs no name server: scheme, credentials, host, port, and
 * literal IP addresses. {@link #check} additionally resolves the host and refuses when <em>any</em> returned
 * address is non-public (loopback, private, link-local including the cloud metadata address, unique-local,
 * multicast, broadcast, CGNAT, benchmarking, IPv4-mapped IPv6). Every redirect hop is checked again.</p>
 *
 * <p>Known limitation: the JDK resolves the name a second time when it connects, so a hostile name server could
 * in theory answer differently (DNS rebinding). The window is tiny and the sandbox has nothing to reach, but we
 * do not claim to close it.</p>
 */
@Component
public final class SsrfGuard {

    /** v0.0.26 🍊 A destination that passed every check. */
    public record Target(URI uri, String host, int port, List<InetAddress> addresses) {

        /** v0.0.26 🍊 Copies the address list so the target is immutable. */
        public Target {
            addresses = List.copyOf(addresses);
        }
    }

    private static final Set<Integer> ALLOWED_PORTS = Set.of(80, 443);

    /** v0.0.26 🍊 Full check: syntax plus every resolved address. */
    public Target check(String url) {
        URI uri = checkSyntax(url);
        String host = host(uri);
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw violation("I could not resolve " + host + ".");
        }
        List<InetAddress> addresses = new ArrayList<>();
        for (InetAddress address : resolved) {
            if (isBlocked(address)) {
                throw violation("The address " + address.getHostAddress() + " behind " + host
                        + " is not a public internet address, so I refuse to open it.");
            }
            addresses.add(address);
        }
        if (addresses.isEmpty()) {
            throw violation("I could not resolve " + host + ".");
        }
        return new Target(uri, host, port(uri), addresses);
    }

    /** v0.0.26 🍊 Lexical check only (no name lookup): scheme, credentials, host, port, literal addresses. */
    public URI checkSyntax(String url) {
        if (url == null || url.isBlank()) {
            throw violation("I need a full http(s) URL.");
        }
        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (URISyntaxException e) {
            throw violation("That is not a valid URL: " + shorten(url) + ".");
        }
        if (!uri.isAbsolute() || uri.getScheme() == null) {
            throw violation("I need an absolute http(s) URL, not " + shorten(url) + ".");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw violation("I can only open http and https pages, not " + scheme + ".");
        }
        if (uri.getRawUserInfo() != null) {
            throw violation("I refuse URLs that carry credentials.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw violation("That URL has no host: " + shorten(url) + ".");
        }
        literalAddress(host).ifPresent(address -> {
            if (isBlocked(address)) {
                throw violation("The address " + host + " is not a public internet address, so I refuse to open it.");
            }
        });
        int port = port(uri);
        if (!ALLOWED_PORTS.contains(port)) {
            throw violation("I only open the standard web ports 80 and 443, not port " + port + ".");
        }
        return uri;
    }

    /** v0.0.26 🍊 True when an address must never be reached from an agent. */
    public static boolean isBlocked(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address v6) {
            byte[] bytes = v6.getAddress();
            if (v6.isIPv4CompatibleAddress() || isIpv4Mapped(bytes)) {
                return true;
            }
            return (bytes[0] & 0xFE) == 0xFC;
        }
        if (address instanceof Inet4Address) {
            int[] b = unsigned(address.getAddress());
            return b[0] == 0                                        // 0.0.0.0/8
                    || b[0] == 127                                  // loopback
                    || b[0] == 10                                   // private
                    || (b[0] == 172 && b[1] >= 16 && b[1] <= 31)    // private
                    || (b[0] == 192 && b[1] == 168)                 // private
                    || (b[0] == 169 && b[1] == 254)                 // link-local (cloud metadata)
                    || (b[0] == 100 && b[1] >= 64 && b[1] <= 127)   // CGNAT
                    || (b[0] == 192 && b[1] == 0 && b[2] == 0)      // IETF protocol assignments
                    || (b[0] == 198 && (b[1] == 18 || b[1] == 19))  // benchmarking
                    || b[0] >= 224;                                 // multicast and reserved, incl. broadcast
        }
        return true;
    }

    /** v0.0.26 🍊 The host of a checked URI, without a trailing dot and in lower case. */
    private static String host(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    /** v0.0.26 🍊 The port of a URI, defaulted by scheme. */
    private static int port(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /** v0.0.26 🍊 The address a host literally is (IPv4/IPv6 text), or empty when it is a name. */
    private static java.util.Optional<InetAddress> literalAddress(String host) {
        String text = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        if (!text.chars().allMatch(c -> Character.digit(c, 16) >= 0 || c == '.' || c == ':' || c == '%')) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(InetAddress.getByName(text));
        } catch (UnknownHostException e) {
            return java.util.Optional.empty();
        }
    }

    /** v0.0.26 🍊 True for ::ffff:a.b.c.d, which would otherwise smuggle an IPv4 address through. */
    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }

    /** v0.0.26 🍊 The four octets of an IPv4 address as unsigned ints. */
    private static int[] unsigned(byte[] raw) {
        return new int[]{raw[0] & 0xFF, raw[1] & 0xFF, raw[2] & 0xFF, raw[3] & 0xFF};
    }

    /** v0.0.26 🍊 A refusal the agent will read (and that the monitor records as a sandbox violation). */
    private static SandboxViolationException violation(String message) {
        return new SandboxViolationException(message);
    }

    /** v0.0.26 🍊 Shortens a URL for an error message. */
    private static String shorten(String url) {
        String text = url.strip().replaceAll("\\s+", " ");
        return text.length() > 80 ? text.substring(0, 80) + "…" : text;
    }
}
