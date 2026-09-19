package ai.yuzu.web;

import ai.yuzu.common.error.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * v0.0.26 🍊 The offline fetcher: it serves {@link WebCorpus} pages through the same extraction path as live
 * pages, so demos and tests behave exactly like real browsing minus the network.
 */
@Component
public class CorpusWebFetcher implements WebFetcher {

    private final WebCorpus corpus;
    private final SsrfGuard guard;

    /** v0.0.26 🍊 Injects the corpus and the guard (URLs are still checked lexically). */
    public CorpusWebFetcher(WebCorpus corpus, SsrfGuard guard) {
        this.corpus = corpus;
        this.guard = guard;
    }

    /** v0.0.26 🍊 The mode this fetcher serves. */
    @Override
    public WebMode mode() {
        return WebMode.CORPUS;
    }

    /** v0.0.26 🍊 Deterministic search over the corpus. */
    @Override
    public List<SearchHit> search(String query, int max) {
        return corpus.search(query, max);
    }

    /** v0.0.26 🍊 Parses a corpus page; unknown URLs fail instead of silently reaching the network. */
    @Override
    public WebPage fetch(String url, int maxChars) {
        guard.checkSyntax(url);
        WebCorpus.Page page = corpus.page(url).orElseThrow(() -> new NotFoundException(
                "I am browsing the offline corpus right now and it does not contain " + url + "."));
        return HtmlExtractor.extract(url, page.url(), page.html(), maxChars);
    }
}
