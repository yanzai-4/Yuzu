package ai.yuzu.web;

import java.util.List;

/** v0.0.26 🍊 Where pages come from: the live internet or the deterministic corpus (one bean per mode). */
public interface WebFetcher {

    /** v0.0.26 🍊 The mode this fetcher serves. */
    WebMode mode();

    /** v0.0.26 🍊 Search results for a query, at most max of them. */
    List<SearchHit> search(String query, int max);

    /** v0.0.26 🍊 One page, already extracted and cut to maxChars. */
    WebPage fetch(String url, int maxChars);
}
