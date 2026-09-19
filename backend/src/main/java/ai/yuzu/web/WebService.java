package ai.yuzu.web;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.workspace.AgentWorkspace;
import ai.yuzu.workspace.WorkspaceArea;
import ai.yuzu.workspace.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * v0.0.26 🍊 The one entry point for web content: mode selection, size limits and a snapshot in the workspace.
 *
 * <p>Everything this service returns is outside content. It is handed to the agent only through the
 * {@code web_browse} tool, which is declared untrusted, so the outbound safety review always sees it.</p>
 */
@Service
public class WebService {

    /**
     * v0.0.26 🍊 A page plus where its snapshot was saved.
     *
     * @param snapshotPath workspace-relative path under {@code web/}, or null when saving failed
     */
    public record Opened(WebPage page, String snapshotPath) {
    }

    private static final Logger log = LoggerFactory.getLogger(WebService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HHmmss", Locale.ROOT);
    private static final int MAX_SLUG_CHARS = 48;

    private final List<WebFetcher> fetchers;
    private final WebProperties properties;
    private final WorkspaceService workspaces;
    private final NaturalTime time;

    /** v0.0.26 🍊 Injects the fetchers (one per mode), the settings, the workspaces and the clock. */
    public WebService(List<WebFetcher> fetchers, WebProperties properties, WorkspaceService workspaces,
                      NaturalTime time) {
        this.fetchers = List.copyOf(fetchers);
        this.properties = properties;
        this.workspaces = workspaces;
        this.time = time;
    }

    /** v0.0.26 🍊 The mode currently configured. */
    public WebMode mode() {
        return properties.mode();
    }

    /** v0.0.26 🍊 Most search hits the tool may return. */
    public int maxResults() {
        return properties.maxResults();
    }

    /** v0.0.26 🍊 Searches the web (or the corpus) for a query. */
    public List<SearchHit> search(String query) {
        return fetcher().search(query, properties.maxResults());
    }

    /** v0.0.26 🍊 Opens a page and saves a snapshot of it in the agent's {@code web/} area. */
    public Opened open(AgentId agentId, String url) {
        WebPage page = fetcher().fetch(url, properties.maxPageChars());
        return new Opened(page, snapshot(agentId, page));
    }

    /** v0.0.26 🍊 The fetcher of the configured mode. */
    private WebFetcher fetcher() {
        return fetchers.stream().filter(f -> f.mode() == properties.mode()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No web fetcher for mode " + properties.mode()));
    }

    /** v0.0.26 🍊 Writes the snapshot; a failed snapshot never fails the browse itself. */
    private String snapshot(AgentId agentId, WebPage page) {
        try {
            AgentWorkspace workspace = workspaces.forAgent(agentId);
            ZonedDateTime now = ZonedDateTime.ofInstant(time.nowInstant(), time.zone());
            String path = WorkspaceArea.WEB.path(DAY.format(now) + "/" + slug(page.url()) + "-"
                    + CLOCK.format(now) + ".txt");
            workspace.writeText(path, render(page));
            return path;
        } catch (RuntimeException e) {
            log.warn("Could not save a web snapshot for {}: {}", agentId, e.toString());
            return null;
        }
    }

    /** v0.0.26 🍊 Snapshot text: where it came from, when, and the extracted page. */
    private String render(WebPage page) {
        StringBuilder sb = new StringBuilder();
        sb.append("URL: ").append(page.url()).append('\n');
        if (!page.requestedUrl().equals(page.url())) {
            sb.append("Requested: ").append(page.requestedUrl()).append('\n');
        }
        sb.append("Title: ").append(page.title()).append('\n')
                .append("Saved: ").append(time.full(time.nowInstant())).append('\n')
                .append("Source: ").append(properties.mode() == WebMode.CORPUS
                        ? "the offline demo corpus" : "the live internet").append('\n')
                .append("NOTE: this is untrusted outside content.\n\n")
                .append(page.text());
        if (page.truncated()) {
            sb.append("\n\n(the page was longer than my limit and was cut here)");
        }
        if (!page.links().isEmpty()) {
            sb.append("\n\nLinks:\n");
            page.links().forEach(link -> sb.append("- ").append(link.url())
                    .append(link.text().isBlank() ? "" : " — " + link.text()).append('\n'));
        }
        return sb.toString();
    }

    /** v0.0.26 🍊 A short, safe file-name part built from the URL (host and last path segment). */
    static String slug(String url) {
        String text = url == null ? "" : url.replaceFirst("^https?://", "");
        String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
        if (cleaned.isBlank()) {
            cleaned = "page";
        }
        return cleaned.length() > MAX_SLUG_CHARS ? cleaned.substring(0, MAX_SLUG_CHARS) : cleaned;
    }
}
