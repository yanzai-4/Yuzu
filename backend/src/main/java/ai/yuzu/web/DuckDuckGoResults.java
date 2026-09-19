package ai.yuzu.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** v0.0.26 🍊 Parses DuckDuckGo's HTML endpoint into search hits, unwrapping its {@code /l/?uddg=} redirects. */
public final class DuckDuckGoResults {

    private static final String RESULTS = "div.result, div.web-result";
    private static final String AD = "result--ad";
    private static final String SPONSORED = "result--sponsored";

    /** v0.0.26 🍊 Static helpers only. */
    private DuckDuckGoResults() {
    }

    /** v0.0.26 🍊 Up to max organic hits, in the order the engine returned them. */
    public static List<SearchHit> parse(String html, int max) {
        Document doc = Jsoup.parse(html == null ? "" : html, "https://duckduckgo.com/");
        List<SearchHit> hits = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Element result : doc.select(RESULTS)) {
            if (result.hasClass(AD) || result.hasClass(SPONSORED)) {
                continue;
            }
            Element anchor = result.selectFirst("a.result__a");
            if (anchor == null) {
                anchor = result.selectFirst("h2 a[href]");
            }
            if (anchor == null) {
                continue;
            }
            String url = unwrap(anchor.absUrl("href").isBlank() ? anchor.attr("href") : anchor.absUrl("href"));
            if (url.isBlank() || !seen.add(url)) {
                continue;
            }
            Element snippet = result.selectFirst(".result__snippet");
            hits.add(new SearchHit(anchor.text().strip(), url, snippet == null ? "" : snippet.text().strip()));
            if (hits.size() >= max) {
                break;
            }
        }
        return List.copyOf(hits);
    }

    /** v0.0.26 🍊 The real destination behind a DuckDuckGo redirect link (or the link itself). */
    static String unwrap(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        String url = href.strip();
        int marker = url.indexOf("uddg=");
        if (marker >= 0) {
            String encoded = url.substring(marker + "uddg=".length());
            int end = encoded.indexOf('&');
            url = URLDecoder.decode(end < 0 ? encoded : encoded.substring(0, end), StandardCharsets.UTF_8);
        } else if (url.startsWith("//")) {
            url = "https:" + url;
        }
        return url.startsWith("http://") || url.startsWith("https://") ? url : "";
    }
}
