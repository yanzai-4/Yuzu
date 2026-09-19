package ai.yuzu.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * v0.0.26 🍊 Typed view of the {@code yuzu.web.*} configuration namespace (the web-browse tool).
 *
 * @param mode           LIVE (real internet) or CORPUS (deterministic offline pages)
 * @param timeout        wall-clock budget of one search or fetch, redirects included
 * @param maxBytes       most bytes read from one response before the download is cut
 * @param maxRedirects   redirect hops followed, each one re-checked against the SSRF guard
 * @param maxResults     most search hits returned to the agent
 * @param maxPageChars   most characters of extracted page text returned to the agent
 * @param searchEndpoint DuckDuckGo HTML endpoint used in LIVE mode
 * @param userAgent      User-Agent sent with every request
 */
@ConfigurationProperties(prefix = "yuzu.web")
public record WebProperties(WebMode mode, Duration timeout, int maxBytes, int maxRedirects, int maxResults,
                            int maxPageChars, String searchEndpoint, String userAgent) {

    /** v0.0.26 🍊 Applies safe defaults for missing values. */
    public WebProperties {
        mode = mode == null ? WebMode.LIVE : mode;
        timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? Duration.ofSeconds(20) : timeout;
        maxBytes = maxBytes <= 0 ? 2 << 20 : Math.min(maxBytes, 16 << 20);
        maxRedirects = maxRedirects <= 0 ? 4 : Math.min(maxRedirects, 10);
        maxResults = maxResults <= 0 ? 6 : Math.min(maxResults, 20);
        maxPageChars = maxPageChars <= 0 ? 8_000 : Math.min(maxPageChars, 200_000);
        searchEndpoint = searchEndpoint == null || searchEndpoint.isBlank()
                ? "https://html.duckduckgo.com/html/" : searchEndpoint;
        userAgent = userAgent == null || userAgent.isBlank()
                ? "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/126.0 Safari/537.36 YuzuAgent/0.0.26" : userAgent;
    }
}
