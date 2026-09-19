package ai.yuzu.web;

import ai.yuzu.common.error.ToolExecutionException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * v0.0.26 🍊 Real browsing: DuckDuckGo's HTML endpoint for search, direct GETs for pages.
 *
 * <p>Every hop passes {@link SsrfGuard}; redirects are followed by hand so each new location is checked again.
 * The body is read through a hard byte cap and the whole call shares one wall-clock deadline, so a slow or
 * endless page can never hold an agent.</p>
 */
@Component
public class LiveWebFetcher implements WebFetcher {

    private static final List<String> TEXT_TYPES = List.of("text/html", "application/xhtml", "text/plain",
            "application/xml", "text/xml");

    private final WebProperties properties;
    private final SsrfGuard guard;
    private final HttpClient http;

    /** v0.0.26 🍊 Builds a redirect-free HTTP/1.1 client (redirects are checked and followed by hand). */
    public LiveWebFetcher(WebProperties properties, SsrfGuard guard) {
        this.properties = properties;
        this.guard = guard;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** v0.0.26 🍊 The mode this fetcher serves. */
    @Override
    public WebMode mode() {
        return WebMode.LIVE;
    }

    /** v0.0.26 🍊 Searches through DuckDuckGo's HTML endpoint and parses the result list. */
    @Override
    public List<SearchHit> search(String query, int max) {
        String url = properties.searchEndpoint() + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&kl=us-en";
        Download download = download(url, Instant.now().plus(properties.timeout()));
        List<SearchHit> hits = DuckDuckGoResults.parse(download.body(), max);
        if (hits.isEmpty() && isChallenge(download.body())) {
            throw failure("The search engine answered with an anti-bot challenge instead of results, so I could "
                    + "not search the live web just now.");
        }
        return hits;
    }

    /** v0.0.26 🍊 True for DuckDuckGo's "unusual traffic" page, which arrives with a 2xx status and no results. */
    private static boolean isChallenge(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("anomaly-modal") || lower.contains("unusual traffic")
                || (lower.contains("challenge") && lower.contains("duckduckgo"));
    }

    /** v0.0.26 🍊 Fetches and extracts one page. */
    @Override
    public WebPage fetch(String url, int maxChars) {
        Download download = download(url, Instant.now().plus(properties.timeout()));
        WebPage page = HtmlExtractor.extract(url, download.finalUrl(), download.body(), maxChars);
        return download.truncated() && !page.truncated()
                ? new WebPage(page.requestedUrl(), page.url(), page.title(), page.text(), page.links(), true)
                : page;
    }

    /** v0.0.26 🍊 One downloaded document. */
    private record Download(String finalUrl, String body, boolean truncated) {
    }

    /** v0.0.26 🍊 GETs a URL, following checked redirects until the deadline or the hop limit. */
    private Download download(String url, Instant deadline) {
        String current = url;
        for (int hop = 0; hop <= properties.maxRedirects(); hop++) {
            SsrfGuard.Target target = guard.check(current);
            Duration left = Duration.between(Instant.now(), deadline);
            if (left.isZero() || left.isNegative()) {
                throw timeout(url);
            }
            HttpResponse<InputStream> response = send(target.uri(), left, url);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("location").orElse(null);
                close(response);
                if (location == null || location.isBlank()) {
                    throw failure("The server at " + target.host() + " redirected me without saying where.");
                }
                current = target.uri().resolve(location).toString();
                continue;
            }
            if (status >= 400) {
                close(response);
                throw failure("The server answered HTTP " + status + " for " + current + ".");
            }
            return body(response, current);
        }
        throw failure("Too many redirects while opening " + url + ".");
    }

    /** v0.0.26 🍊 Sends one GET with the remaining time budget. */
    private HttpResponse<InputStream> send(URI uri, Duration left, String url) {
        HttpRequest request = HttpRequest.newBuilder(uri).GET().timeout(left)
                .header("User-Agent", properties.userAgent())
                .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.1")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw failure("I could not reach " + uri.getHost() + " (" + e.getClass().getSimpleName() + ").");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw timeout(url);
        }
    }

    /** v0.0.26 🍊 Reads at most maxBytes of a textual body and decodes it with the declared charset. */
    private Download body(HttpResponse<InputStream> response, String finalUrl) {
        String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
        if (!contentType.isBlank() && TEXT_TYPES.stream().noneMatch(contentType::contains)) {
            close(response);
            throw failure("That link is not a readable web page (" + contentType.split(";")[0] + ").");
        }
        byte[] raw;
        boolean truncated;
        try (InputStream in = response.body()) {
            raw = in.readNBytes(properties.maxBytes());
            truncated = in.read() >= 0;
        } catch (IOException e) {
            throw failure("The download of " + finalUrl + " broke off.");
        }
        return new Download(finalUrl, new String(raw, charset(contentType)), truncated);
    }

    /** v0.0.26 🍊 The charset a content-type header declares (UTF-8 otherwise). */
    private static Charset charset(String contentType) {
        int at = contentType.indexOf("charset=");
        if (at < 0) {
            return StandardCharsets.UTF_8;
        }
        String name = contentType.substring(at + "charset=".length()).split("[;\\s]")[0].replace("\"", "");
        try {
            return Optional.of(name).filter(n -> !n.isBlank()).map(Charset::forName).orElse(StandardCharsets.UTF_8);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return StandardCharsets.UTF_8;
        }
    }

    /** v0.0.26 🍊 Releases a response we are not going to read. */
    private static void close(HttpResponse<InputStream> response) {
        try (InputStream ignored = response.body()) {
            // draining is not needed: closing is enough to release the connection
        } catch (IOException e) {
            // nothing useful to do while giving up on a response
        }
    }

    /** v0.0.26 🍊 A readable failure the agent can act on. */
    private static ToolExecutionException failure(String message) {
        return new ToolExecutionException(message);
    }

    /** v0.0.26 🍊 The wall-clock budget ran out. */
    private static ToolExecutionException timeout(String url) {
        return new ToolExecutionException("Opening " + url + " took too long, so I gave up.");
    }
}
