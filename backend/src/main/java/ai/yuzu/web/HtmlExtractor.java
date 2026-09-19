package ai.yuzu.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** v0.0.26 🍊 Turns raw HTML into the title, readable block text and a few absolute links (Jsoup, no network). */
public final class HtmlExtractor {

    /** v0.0.26 🍊 Most links returned with a page. */
    public static final int MAX_LINKS = 10;

    private static final String NOISE = "script, style, noscript, iframe, svg, canvas, template, form, nav, footer,"
            + " header, aside";
    private static final String BLOCKS = "h1, h2, h3, h4, h5, h6, p, li, dd, dt, pre, blockquote, td, th, figcaption";
    private static final int MAX_LINK_TEXT = 120;

    /** v0.0.26 🍊 Static helpers only. */
    private HtmlExtractor() {
    }

    /** v0.0.26 🍊 Extracts a page; text is cut to maxChars and truncated() then says so. */
    public static WebPage extract(String requestedUrl, String finalUrl, String html, int maxChars) {
        Document doc = Jsoup.parse(html == null ? "" : html, finalUrl == null ? "" : finalUrl);
        String title = doc.title() == null ? "" : doc.title().strip();
        doc.select(NOISE).remove();
        Element root = firstOf(doc, "main", "article", "[role=main]", "#content", "body");
        String text = blockText(root == null ? doc : root);
        boolean truncated = text.length() > maxChars;
        return new WebPage(requestedUrl, finalUrl, title, truncated ? text.substring(0, maxChars) : text,
                links(doc), truncated);
    }

    /** v0.0.26 🍊 The first element matching any of the selectors, or null. */
    private static Element firstOf(Document doc, String... selectors) {
        for (String selector : selectors) {
            Element found = doc.selectFirst(selector);
            if (found != null && !found.text().isBlank()) {
                return found;
            }
        }
        return null;
    }

    /** v0.0.26 🍊 Block-level text, one paragraph per line, without repeating nested blocks. */
    private static String blockText(Element root) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        for (Element block : root.select(BLOCKS)) {
            if (block.select(BLOCKS).isEmpty()) {
                String line = block.text().strip();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
        if (lines.isEmpty()) {
            String fallback = root.text().strip();
            return fallback;
        }
        return String.join("\n", lines);
    }

    /** v0.0.26 🍊 Up to MAX_LINKS absolute, distinct http(s) links with their anchor text. */
    private static List<WebLink> links(Document doc) {
        List<WebLink> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        Elements anchors = doc.select("a[href]");
        for (Element anchor : anchors) {
            String href = anchor.absUrl("href");
            if (href.isBlank() || !(href.startsWith("http://") || href.startsWith("https://")) || !seen.add(href)) {
                continue;
            }
            String text = anchor.text().strip();
            out.add(new WebLink(href, text.length() > MAX_LINK_TEXT ? text.substring(0, MAX_LINK_TEXT) + "…" : text));
            if (out.size() >= MAX_LINKS) {
                break;
            }
        }
        return out;
    }
}
