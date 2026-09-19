package ai.yuzu.web;

/**
 * v0.0.26 🍊 One search result. Title and snippet are UNTRUSTED outside content.
 *
 * @param title   result title
 * @param url     absolute result URL
 * @param snippet the short description the search engine showed
 */
public record SearchHit(String title, String url, String snippet) {
}
