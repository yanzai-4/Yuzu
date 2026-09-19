package ai.yuzu.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.26 🍊 Page extraction, DuckDuckGo result parsing and the deterministic corpus, all without a network. */
class WebExtractionTest {

    private static final String PAGE = """
            <html><head><title>Citrus Spark competitor brief</title>
            <style>body{color:red}</style><script>window.x=1;alert('no')</script></head>
            <body><nav>Home | About</nav>
            <main><h1>Sparkling water in 2026</h1>
            <p>Yuzu Spark leads the flavored seltzer segment with 31% share.</p>
            <p>Lime Fizz grew 12% after its summer launch.</p>
            <ul><li>Retail price: $2.40</li></ul>
            <a href="/reports/2026">The full 2026 report</a>
            </main><footer>© Citrus Weekly</footer></body></html>
            """;

    /** v0.0.26 🍊 Title, readable text and links come out; scripts and styles never do. */
    @Test
    void extractsReadableText() {
        WebPage page = HtmlExtractor.extract("https://news.example.com/brief",
                "https://news.example.com/brief", PAGE, 10_000);
        assertThat(page.title()).isEqualTo("Citrus Spark competitor brief");
        assertThat(page.text()).contains("Sparkling water in 2026").contains("31% share")
                .contains("Retail price: $2.40");
        assertThat(page.text()).doesNotContain("window.x").doesNotContain("color:red");
        assertThat(page.truncated()).isFalse();
        assertThat(page.links()).anyMatch(l -> l.url().equals("https://news.example.com/reports/2026"));
    }

    /** v0.0.26 🍊 Long pages are cut at the configured size and say so. */
    @Test
    void truncatesLongPages() {
        WebPage page = HtmlExtractor.extract("https://news.example.com/brief",
                "https://news.example.com/brief", PAGE, 40);
        assertThat(page.text()).hasSizeLessThanOrEqualTo(40);
        assertThat(page.truncated()).isTrue();
    }

    /** v0.0.26 🍊 DuckDuckGo's HTML endpoint is parsed into hits, unwrapping its redirect links. */
    @Test
    void parsesDuckDuckGoResults() {
        String html = """
                <div class="serp__results">
                  <div class="result results_links results_links_deep web-result">
                    <h2 class="result__title">
                      <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fnews.example.com%2Fbrief&amp;rut=x">
                        Citrus Spark competitor brief</a></h2>
                    <a class="result__snippet">Yuzu Spark leads with 31% share.</a>
                  </div>
                  <div class="result results_links result--ad">
                    <a class="result__a" href="https://ads.example.com/buy">Buy seltzer now</a></div>
                  <div class="result web-result">
                    <a class="result__a" href="https://trends.example.org/seltzer">Seltzer trends 2026</a>
                    <a class="result__snippet">The segment grew 12%.</a></div>
                </div>
                """;
        List<SearchHit> hits = DuckDuckGoResults.parse(html, 5);
        assertThat(hits).hasSize(2);
        assertThat(hits.getFirst().title()).isEqualTo("Citrus Spark competitor brief");
        assertThat(hits.getFirst().url()).isEqualTo("https://news.example.com/brief");
        assertThat(hits.getFirst().snippet()).contains("31% share");
        assertThat(hits.get(1).url()).isEqualTo("https://trends.example.org/seltzer");
    }

    /** v0.0.26 🍊 The corpus is deterministic, searchable and contains the prompt-injection demo page. */
    @Test
    void corpusIsDeterministicAndHasAnInjectionPage() {
        WebCorpus corpus = new WebCorpus();
        List<SearchHit> first = corpus.search("citrus spark competitors", 5);
        List<SearchHit> again = corpus.search("citrus spark competitors", 5);
        assertThat(first).isNotEmpty().isEqualTo(again);
        assertThat(corpus.page(first.getFirst().url())).isPresent();
        assertThat(corpus.pages()).anyMatch(p -> p.injection());
        WebCorpus.Page injection = corpus.pages().stream().filter(WebCorpus.Page::injection).findFirst().orElseThrow();
        assertThat(injection.html().toLowerCase()).contains("ignore all previous instructions");
        assertThat(corpus.page("https://nothing.example.com/missing")).isEmpty();
    }
}
