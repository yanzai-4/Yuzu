package ai.yuzu.llm;

import ai.yuzu.common.concurrent.CancelToken;
import ai.yuzu.common.error.LlmAuthException;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.capability.CapabilityRegistry;
import ai.yuzu.llm.provider.LlmMessage;
import ai.yuzu.llm.provider.LlmResult;
import ai.yuzu.llm.provider.OpenAiCompatibleProvider;
import ai.yuzu.llm.provider.ProviderEndpoint;
import ai.yuzu.llm.structured.OutputStrategy;
import ai.yuzu.settings.AppSettingRepository;
import ai.yuzu.settings.TierSettings;
import ai.yuzu.support.FakeLlmServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** v0.0.8 🍊 Provider parsing, streaming, capability learning, transport retries and auth errors. */
class LlmExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private FakeLlmServer server;
    private LlmExecutor executor;
    private CapabilityRegistry registry;
    private final List<Long> sleeps = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeLlmServer();
        AppSettingRepository store = mock(AppSettingRepository.class);
        when(store.get(anyString())).thenReturn(Optional.empty());
        registry = new CapabilityRegistry(store, new Jsons(mapper),
                new NaturalTime(Clock.systemUTC(), ZoneId.of("America/Los_Angeles")));
        executor = new LlmExecutor(new OpenAiCompatibleProvider(mapper, Duration.ofSeconds(5)), registry, sleeps::add);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    /** v0.0.8 🍊 Text, finish reason and cached-token usage are parsed. */
    @Test
    void parsesCompletionAndUsage() {
        server.enqueue(200, FakeLlmServer.completion("hello", 1200, 1024, 5));
        LlmResult result = executor.execute(call("gpt-4.1-mini", false), OutputStrategy.PROMPT_ONLY, null,
                new CancelToken(), AttemptObserver.NONE);
        assertThat(result.text()).isEqualTo("hello");
        assertThat(result.finishReason()).isEqualTo("stop");
        assertThat(result.usage().promptTokens()).isEqualTo(1200);
        assertThat(result.usage().cachedTokens()).isEqualTo(1024);
        assertThat(result.usage().cacheReported()).isTrue();
    }

    /** v0.0.8 🍊 Streaming aggregates deltas and reads usage from the final chunk. */
    @Test
    void streamsDeltas() {
        server.enqueueStream(List.of(
                "{\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\"lo\"},\"finish_reason\":\"stop\"}]}",
                "{\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,\"prompt_tokens_details\":{\"cached_tokens\":0}}}"));
        List<String> deltas = new ArrayList<>();
        LlmResult result = executor.execute(call("gpt-4.1-mini", true), OutputStrategy.PROMPT_ONLY, deltas::add,
                new CancelToken(), AttemptObserver.NONE);
        assertThat(deltas).containsExactly("Hel", "lo");
        assertThat(result.text()).isEqualTo("Hello");
        assertThat(result.usage().completionTokens()).isEqualTo(2);
        assertThat(result.ttftMs()).isNotNull();
    }

    /** v0.0.8 🍊 A 400 naming temperature is learned and the request is resent without it. */
    @Test
    void learnsUnsupportedTemperature() throws Exception {
        server.enqueue(400, "{\"error\":{\"message\":\"Unsupported parameter: 'temperature'\",\"param\":\"temperature\"}}")
                .enqueue(200, FakeLlmServer.completion("ok", 10, 0, 1));
        LlmCall call = new LlmCall(endpoint(), new TierSettings("custom-chat", null, 100),
                List.of(LlmMessage.user("hi")), null, null, null, false, 0.2, null);
        LlmResult result = executor.execute(call, OutputStrategy.PROMPT_ONLY, null, new CancelToken(), AttemptObserver.NONE);
        assertThat(result.text()).isEqualTo("ok");
        assertThat(mapper.readTree(server.requests().get(0)).has("temperature")).isTrue();
        assertThat(mapper.readTree(server.requests().get(1)).has("temperature")).isFalse();
        assertThat(registry.get(endpoint(), "custom-chat").temperature()).isFalse();
    }

    /** v0.0.8 🍊 max_tokens is switched to max_completion_tokens when the provider asks for it. */
    @Test
    void switchesMaxTokensField() throws Exception {
        server.enqueue(400, "{\"error\":{\"message\":\"'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead.\",\"param\":\"max_tokens\"}}")
                .enqueue(200, FakeLlmServer.completion("ok", 10, 0, 1));
        executor.execute(call("custom-chat", false), OutputStrategy.PROMPT_ONLY, null, new CancelToken(), AttemptObserver.NONE);
        assertThat(mapper.readTree(server.requests().get(1)).has("max_completion_tokens")).isTrue();
    }

    /** v0.0.8 🍊 429 with retry-after-ms is retried with the suggested wait. */
    @Test
    void retriesRateLimits() {
        server.enqueue(429, "{\"error\":{\"message\":\"slow down\"}}", Map.of("retry-after-ms", "1500"))
                .enqueue(200, FakeLlmServer.completion("ok", 10, 0, 1));
        LlmResult result = executor.execute(call("gpt-4.1-mini", false), OutputStrategy.PROMPT_ONLY, null,
                new CancelToken(), AttemptObserver.NONE);
        assertThat(result.text()).isEqualTo("ok");
        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.getFirst()).isGreaterThanOrEqualTo(1500L);
    }

    /** v0.0.8 🍊 401 is not retried. */
    @Test
    void authErrorsAreFinal() {
        server.enqueue(401, "{\"error\":{\"message\":\"bad key\"}}");
        assertThatThrownBy(() -> executor.execute(call("gpt-4.1-mini", false), OutputStrategy.PROMPT_ONLY, null,
                new CancelToken(), AttemptObserver.NONE)).isInstanceOf(LlmAuthException.class);
        assertThat(server.requests()).hasSize(1);
    }

    /** v0.0.8 🍊 A rejected response_format downgrades the strategy and signals the caller. */
    @Test
    void formatRejectionDowngradesStrategy() {
        server.enqueue(400, "{\"error\":{\"message\":\"Invalid parameter: 'response_format' of type 'json_schema' is not supported with this model\",\"param\":\"response_format\"}}");
        LlmCall call = new LlmCall(endpoint(), new TierSettings("custom-chat", null, 100),
                List.of(LlmMessage.user("hi")), "probe", mapper.createObjectNode().put("type", "object"), null, false,
                null, null);
        assertThatThrownBy(() -> executor.execute(call, OutputStrategy.JSON_SCHEMA_STRICT, null, new CancelToken(),
                AttemptObserver.NONE)).isInstanceOf(StrategyDowngradedException.class);
        assertThat(registry.get(endpoint(), "custom-chat").strategy()).isEqualTo(OutputStrategy.JSON_OBJECT);
    }

    /** v0.0.8 🍊 Reasoning models get no temperature and use max_completion_tokens (seeded rule). */
    @Test
    void seededRulesForReasoningModels() throws Exception {
        server.enqueue(200, FakeLlmServer.completion("ok", 10, 0, 1));
        LlmCall call = new LlmCall(endpoint(), new TierSettings("gpt-5-mini", "minimal", 100),
                List.of(LlmMessage.user("hi")), null, null, "yuzu:CHAT:agent-3fa9", false, 0.7, null);
        executor.execute(call, OutputStrategy.PROMPT_ONLY, null, new CancelToken(), AttemptObserver.NONE);
        var body = mapper.readTree(server.requests().getFirst());
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.get("reasoning_effort").asText()).isEqualTo("minimal");
        assertThat(body.has("max_completion_tokens")).isTrue();
        assertThat(body.has("prompt_cache_key")).isFalse();
    }

    private ProviderEndpoint endpoint() {
        return new ProviderEndpoint(server.baseUrl(), "sk-test-key");
    }

    private LlmCall call(String model, boolean stream) {
        return new LlmCall(endpoint(), new TierSettings(model, null, 100), List.of(LlmMessage.user("hi")), null, null,
                null, stream, null, null);
    }
}
