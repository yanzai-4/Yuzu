package ai.yuzu.llm;

import ai.yuzu.common.concurrent.CancelToken;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.config.YuzuProperties;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.prompt.SegmentRank;
import ai.yuzu.llm.structured.SchemaInstructions;
import ai.yuzu.llm.structured.SemanticCheck;
import ai.yuzu.llm.structured.StrictSchemaFactory;
import ai.yuzu.llm.structured.StructuredResult;
import ai.yuzu.llm.usage.TokenMeter;
import ai.yuzu.settings.LlmProvider;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.support.FakeLlmServer;
import ai.yuzu.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v0.0.11 🍊 Gateway end to end against a fake provider: metering, llm_call rows, payloads, local cache, probe. */
@IntegrationTest
@AutoConfigureMockMvc
class LlmGatewayIntegrationTest {

    /** v0.0.11 🍊 Sample output. */
    record Verdict(String reasoning, boolean safe) {
    }

    @Autowired
    private LlmGateway gateway;
    @Autowired
    private SettingsService settings;
    @Autowired
    private TokenMeter meter;
    @Autowired
    private LlmCallRecorder recorder;
    @Autowired
    private StrictSchemaFactory schemas;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private YuzuProperties properties;

    private FakeLlmServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeLlmServer();
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-1234");
    }

    @AfterEach
    void tearDown() {
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.11 🍊 A structured call is metered, recorded (row + payload without key) and cacheable calls hit locally. */
    @Test
    void structuredCallIsMeteredRecordedAndCached() throws Exception {
        AgentId agent = AgentId.of("agent-0b0b");
        server.enqueue(200, FakeLlmServer.completion("{\"reasoning\":\"fine\",\"safe\":true}", 1500, 1024, 20));
        LlmCallContext ctx = new LlmCallContext(agent, "SAFETY", ModelTier.DEFAULT, "trace-1", new CancelToken());

        StructuredResult<Verdict> first = gateway.structured(ctx, Verdict.class, this::prompt, SemanticCheck.none(), true);
        StructuredResult<Verdict> second = gateway.structured(ctx, Verdict.class, this::prompt, SemanticCheck.none(), true);
        assertThat(first.value().safe()).isTrue();
        assertThat(second).isSameAs(first);
        assertThat(server.requests()).hasSize(1);

        var snapshot = meter.snapshot();
        var row = snapshot.byAgent().stream().filter(r -> r.key().equals("agent-0b0b")).findFirst().orElseThrow();
        assertThat(row.calls()).isEqualTo(1);
        assertThat(row.promptTokens()).isEqualTo(1500);
        assertThat(row.cachedTokens()).isEqualTo(1024);
        assertThat(row.hitRate()).isCloseTo(1024.0 / 1500, org.assertj.core.data.Offset.offset(0.001));
        assertThat(snapshot.localCacheHits()).isGreaterThanOrEqualTo(1);

        recorder.flush();
        String payloadPath = jdbc.sql("SELECT payload_path FROM llm_call WHERE agent_id = 'agent-0b0b'")
                .query(String.class).single();
        Path file = properties.workspaceRootPath().resolve("agent-0b0b").resolve(payloadPath);
        for (int i = 0; i < 50 && !Files.exists(file); i++) {
            Thread.sleep(20);
        }
        String payload = Files.readString(file);
        assertThat(payload).contains("\"response_format\"").doesNotContain("sk-fake-provider-key");
    }

    /** v0.0.11 🍊 The console test probes all three tiers. */
    @Test
    void probeTestsEveryTier() throws Exception {
        for (int i = 0; i < 3; i++) {
            server.enqueue(200, FakeLlmServer.completion("{\"ok\":true,\"echo\":\"citrus\"}", 1200, 0, 5));
        }
        JsonNode result = mapper.readTree(mvc.perform(post("/api/settings/llm/test")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (String tier : new String[]{"IMPORTANT", "DEFAULT", "LIGHT"}) {
            assertThat(result.get("tiers").get(tier).get("ok").asBoolean()).isTrue();
            assertThat(result.get("tiers").get(tier).get("strategy").asText()).isEqualTo("JSON_SCHEMA_STRICT");
        }
        JsonNode usage = mapper.readTree(mvc.perform(get("/api/usage")).andReturn().getResponse().getContentAsString());
        assertThat(usage.get("totals").get("calls").asLong()).isGreaterThanOrEqualTo(3);
    }

    private ai.yuzu.llm.prompt.Prompt prompt(ai.yuzu.llm.structured.OutputStrategy strategy) {
        return PromptBuilder.start("HANDBOOK", "Review.\n" + SchemaInstructions.forStrategy(strategy,
                        schemas.schemaText(Verdict.class)))
                .add(SegmentRank.S7_STIMULUS, "Content to review: hello")
                .build("Saturday, September 19, 2026 at 11:32:05 AM PDT");
    }
}
