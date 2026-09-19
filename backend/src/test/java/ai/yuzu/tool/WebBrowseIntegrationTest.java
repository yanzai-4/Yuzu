package ai.yuzu.tool;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Permission;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.tool.impl.web.WebBrowseTool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.web.WebCorpus;
import ai.yuzu.workspace.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.26 🍊 web_browse on the deterministic corpus: search, open, snapshot, untrusted output, guards. */
@IntegrationTest
@TestPropertySource(properties = "yuzu.web.mode=CORPUS")
class WebBrowseIntegrationTest {

    private static final Pattern SNAPSHOT = Pattern.compile("web/[0-9A-Za-z_./-]+");

    @Autowired
    private AgentService agents;
    @Autowired
    private WebBrowseTool tool;
    @Autowired
    private WebCorpus corpus;
    @Autowired
    private WorkspaceService workspaces;
    @Autowired
    private AgentRuntimeManager runtimes;
    @Autowired
    private ModuleDeps deps;
    @Autowired
    private NaturalTime time;
    @Autowired
    private JdbcClient jdbc;

    private AgentProfile lime;
    private AgentProfile pomelo;

    @BeforeEach
    void setUp() {
        String roomId = newRoom();
        lime = agents.createNamed(roomId, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null), "Lime");
        pomelo = agents.createNamed(roomId,
                new CreateAgentRequest(Role.CUSTOMER_LIAISON, null, null, null, null, null), "Pomelo");
    }

    /** v0.0.26 🍊 The tool is declared untrusted so its text always passes the outbound safety review. */
    @Test
    void specIsUntrustedAndNeedsWebBrowse() {
        assertThat(tool.spec().name()).isEqualTo("web_browse");
        assertThat(tool.spec().trusted()).isFalse();
        assertThat(tool.spec().permissions()).containsExactly(Permission.WEB_BROWSE);
        assertThat(tool.spec().source()).contains("internet");
    }

    /** v0.0.26 🍊 A search returns titles, URLs and snippets from the corpus. */
    @Test
    void searchesTheCorpus() {
        ToolResult result = tool.execute(context(lime), new WebBrowseTool.Args("citrus spark competitors", null));
        assertThat(result.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(result.output()).contains("I searched the web").contains("https://");
        assertThat(result.output()).contains(corpus.pages().getFirst().title());
    }

    /** v0.0.26 🍊 Opening a page returns its text and saves a snapshot inside the agent's web/ area. */
    @Test
    void opensAPageAndSavesASnapshot() {
        String url = corpus.pages().getFirst().url();
        ToolResult result = tool.execute(context(lime), new WebBrowseTool.Args(null, url));
        assertThat(result.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(result.output()).contains(url).contains("saved");
        Matcher matcher = SNAPSHOT.matcher(result.output());
        assertThat(matcher.find()).isTrue();
        String path = matcher.group();
        assertThat(workspaces.forAgent(lime.agentId()).exists(path)).isTrue();
        assertThat(workspaces.forAgent(lime.agentId()).readText(path, 65_536).text()).contains(url);
    }

    /** v0.0.26 🍊 The injection page is returned verbatim (untrusted), so the outbound review can mask it. */
    @Test
    void returnsTheInjectionPageForReview() {
        WebCorpus.Page injection = corpus.pages().stream().filter(WebCorpus.Page::injection).findFirst().orElseThrow();
        ToolResult result = tool.execute(context(lime), new WebBrowseTool.Args(null, injection.url()));
        assertThat(result.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(result.output().toLowerCase()).contains("ignore all previous instructions");
        assertThat(tool.spec().trusted()).isFalse();
    }

    /** v0.0.26 🍊 Loopback and other private destinations are refused even in corpus mode. */
    @Test
    void refusesPrivateAddresses() {
        ToolResult result = tool.execute(context(lime), new WebBrowseTool.Args(null, "http://127.0.0.1:8080/admin"));
        assertThat(result.status()).isEqualTo(ToolResult.Status.ERROR);
        assertThat(result.output()).contains("127.0.0.1");
    }

    /** v0.0.26 🍊 Unknown corpus pages fail cleanly instead of reaching the network. */
    @Test
    void reportsPagesOutsideTheCorpus() {
        ToolResult result = tool.execute(context(lime), new WebBrowseTool.Args(null, "https://absent.example.com/x"));
        assertThat(result.status()).isEqualTo(ToolResult.Status.ERROR);
        assertThat(result.output()).contains("offline");
    }

    /** v0.0.26 🍊 Exactly one of query and url is required. */
    @Test
    void requiresAQueryOrAUrl() {
        assertThat(tool.execute(context(lime), new WebBrowseTool.Args(null, null)).status())
                .isEqualTo(ToolResult.Status.ERROR);
    }

    /** v0.0.26 🍊 An agent without WEB_BROWSE is refused inside execute (defense in depth). */
    @Test
    void refusesAgentsWithoutThePermission() {
        assertThatThrownBy(() -> tool.execute(context(pomelo), new WebBrowseTool.Args("anything", null)))
                .isInstanceOf(PermissionDeniedException.class);
    }

    /** v0.0.26 🍊 Creates one isolated room for this test. */
    private String newRoom() {
        String id = String.format("room-%04x", ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        jdbc.sql("INSERT INTO room (id, name, created_at) VALUES (:id, 'Web room', UTC_TIMESTAMP(3))")
                .param("id", id).update();
        return id;
    }

    /** v0.0.26 🍊 A direct tool context for one coworker. */
    private ToolContext context(AgentProfile profile) {
        AgentContext ctx = runtimes.require(profile.agentId()).context("trace-web", null, time);
        return new ToolContext(ctx, "batch-web", "call-" + ThreadLocalRandom.current().nextInt(1_000_000), 0,
                "look something up", 0,
                deps.reporter().start(profile.agentId(), "TOOL", "test", ctx.traceId(), null));
    }
}
