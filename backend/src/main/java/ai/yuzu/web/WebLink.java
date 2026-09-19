package ai.yuzu.web;

/**
 * v0.0.26 🍊 One outgoing link of a fetched page (untrusted text, absolute URL).
 *
 * @param url  absolute destination
 * @param text the anchor text as written on the page
 */
public record WebLink(String url, String text) {
}
