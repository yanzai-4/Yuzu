package ai.yuzu.web;

import java.util.List;

/**
 * v0.0.26 🍊 One fetched page after extraction. Everything in it is UNTRUSTED outside content.
 *
 * @param requestedUrl the URL the agent asked for
 * @param url          the URL the content actually came from (after redirects)
 * @param title        page title (may be empty)
 * @param text         readable text, already cut to the configured size
 * @param links        a few outgoing links, absolute
 * @param truncated    true when the page was longer than the size budget
 */
public record WebPage(String requestedUrl, String url, String title, String text, List<WebLink> links,
                      boolean truncated) {

    /** v0.0.26 🍊 Copies the link list so the page is immutable. */
    public WebPage {
        links = List.copyOf(links);
    }
}
