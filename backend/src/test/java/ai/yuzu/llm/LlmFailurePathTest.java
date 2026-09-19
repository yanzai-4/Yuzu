package ai.yuzu.llm;

import ai.yuzu.common.concurrent.CancelToken;
import ai.yuzu.common.error.LlmOutputInvalidException;
import ai.yuzu.common.error.LlmTransportException;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.capability.CapabilityRegistry;
import ai.yuzu.llm.prompt.Prompt;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.prompt.SegmentRank;
import ai.yuzu.llm.provider.ChatProvider;
import ai.yuzu.llm.provider.LlmMessage;
import ai.yuzu.llm.provider.LlmRequest;
import ai.yuzu.llm.provider.LlmResult;
import ai.yuzu.llm.provider.OpenAiCompatibleProvider;
import ai.yuzu.llm.provider.ProviderEndpoint;
import ai.yuzu.llm.provider.ProviderRequestException;
import ai.yuzu.llm.provider.ResponseFormat;
import ai.yuzu.llm.provider.StreamSink;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.OutputStrategy;
import ai.yuzu.llm.structured.SchemaInstructions;
import ai.yuzu.llm.structured.SchemaValidator;
import ai.yuzu.llm.structured.SemanticCheck;
import ai.yuzu.llm.structured.StrictSchemaFactory;
import ai.yuzu.llm.structured.StructuredCaller;
import ai.yuzu.llm.structured.StructuredResult;
import ai.yuzu.llm.usage.Usage;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * v0.0.31 🍊 Failure branches of the model layer that no other test covers.
 *
 * <p>Transport: 5xx and I/O errors are retried with 1/2/4 s backoff and then give up; {@code Retry-After} in
 * seconds is honoured; a non-retryable 4xx fails at once; the capability-learning budget is finite.
 * Structured output: a model that silently returns invalid strict JSON is marked unreliable and downgraded;
 * an endpoint that rejects every JSON format exhausts the downgrade chain instead of looping.</p>
 */
class LlmFailurePathTest {

    /** v0.0.31 🍊 Sample module output for the structured tests. */
    record Answer(@Desc("Short reasoning") String reasoning, boolean done) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Long> sleeps = new ArrayList<>();
    private FakeLlmServer server;
    private CapabilityRegistry registry;
    private LlmExecutor httpExecutor;
    private StrictSchemaFactory schemas;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeLlmServer();
        registry = newRegistry();
        schemas = new StrictSchemaFactory(mapper);
        httpExecutor = new LlmExecutor(new OpenAiCompatibleProvider(mapper, Duration.ofSeconds(5)), registry,
                sleeps::add);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    /** v0.0.31 🍊 A 5xx is retried three times (1/2/4 s) and then surfaces as a retryable transport failure. */
    @Test
    void serverErrorsAreRetriedThenGiveUp() {
        for (int i = 0; i < 6; i++) {
            server.enqueue(503, "{\"error\":{\"message\":\"upstream unavailable\"}}");
        }
        assertThatThrownBy(() -> httpExecutor.execute(httpCall("gpt-4.1-mini"), OutputStrategy.PROMPT_ONLY, null,
                new CancelToken(), AttemptObserver.NONE))
                .isInstanceOf(LlmTransportException.class)
                .satisfies(e -> assertThat(((LlmTransportException) e).status()).isEqualTo(503));
        assertThat(server.requests()).as("1 call + 3 retries").hasSize(4);
        assertThat(sleeps).hasSize(3);
        assertThat(sleeps.get(0)).isBetween(1_000L, 1_250L);
        assertThat(sleeps.get(1)).isBetween(2_000L, 2_250L);
        assertThat(sleeps.get(2)).isBetween(4_000L, 4_250L);
    }

    /** v0.0.31 🍊 A 5xx followed by a good answer recovers without the caller noticing. */
    @Test
    void serverErrorRecoversOnRetry() {
        server.enqueue(500, "{\"error\":{\"message\":\"boom\"}}")
                .enqueue(200, FakeLlmServer.completion("recovered", 10, 0, 1));
        LlmResult result = httpExecutor.execute(httpCall("gpt-4.1-mini"), OutputStrategy.PROMPT_ONLY, null,
                new CancelToken(), AttemptObserver.NONE);
        assertThat(result.text()).isEqualTo("recovered");
        assertThat(sleeps).hasSize(1);
    }

    /** v0.0.31 🍊 Retry-After in whole seconds is honoured (not only the retry-after-ms variant). */
    @Test
    void retryAfterSecondsIsHonoured() {
        server.enqueue(429, "{\"error\":{\"message\":\"slow down\"}}", Map.of("Retry-After", "7"))
                .enqueue(200, FakeLlmServer.completion("ok", 10, 0, 1));
        httpExecutor.execute(httpCall("gpt-4.1-mini"), OutputStrategy.PROMPT_ONLY, null, new CancelToken(),
                AttemptObserver.NONE);
        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.getFirst()).isGreaterThanOrEqualTo(7_000L);
    }

    /** v0.0.31 🍊 A 404 (unknown model) is not retried: retrying cannot fix it. */
    @Test
    void unknownModelFailsImmediately() {
        server.enqueue(404, "{\"error\":{\"message\":\"The model 'nope' does not exist\"}}");
        assertThatThrownBy(() -> httpExecutor.execute(httpCall("nope"), OutputStrategy.PROMPT_ONLY, null,
                new CancelToken(), AttemptObserver.NONE)).isInstanceOf(ai.yuzu.common.error.YuzuException.class);
        assertThat(server.requests()).hasSize(1);
        assertThat(sleeps).isEmpty();
    }

    /** v0.0.31 🍊 An I/O failure (no response at all) is retried and then reported, never swallowed. */
    @Test
    void transportFailuresAreBounded() {
        ScriptedProvider provider = new ScriptedProvider();
        for (int i = 0; i < 8; i++) {
            provider.fail(() -> new LlmTransportException("connection reset", 0, true, 0, null));
        }
        LlmExecutor executor = new LlmExecutor(provider, registry, sleeps::add);
        assertThatThrownBy(() -> executor.execute(httpCall("gpt-4.1-mini"), OutputStrategy.PROMPT_ONLY, null,
                new CancelToken(), AttemptObserver.NONE)).isInstanceOf(LlmTransportException.class);
        assertThat(provider.requests()).as("a logical call never sends more than 4 transport attempts").hasSize(4);
    }

    /** v0.0.31 🍊 Capability learning is finite: a provider that keeps rejecting a parameter cannot loop. */
    @Test
    void capabilityLearningIsBounded() {
        ScriptedProvider provider = new ScriptedProvider();
        for (int i = 0; i < 20; i++) {
            provider.fail(() -> new ProviderRequestException(400,
                    "Unsupported parameter: 'temperature'", "temperature", null));
        }
        LlmExecutor executor = new LlmExecutor(provider, registry, sleeps::add);
        LlmCall call = new LlmCall(endpoint(), new TierSettings("custom-chat", null, 100),
                List.of(LlmMessage.user("hi")), null, null, null, false, 0.3, null);
        assertThatThrownBy(() -> executor.execute(call, OutputStrategy.PROMPT_ONLY, null, new CancelToken(),
                AttemptObserver.NONE)).isInstanceOf(ProviderRequestException.class);
        // The first rejection switches temperature off; the second cannot learn anything new and gives up.
        assertThat(provider.requests()).hasSizeLessThanOrEqualTo(2 + LlmExecutor.MAX_LEARN_STEPS);
    }

    /** v0.0.31 🍊 Three silent strict-schema failures mark the model unreliable and downgrade it mid-call. */
    @Test
    void repeatedInvalidStrictOutputDowngradesTheModel() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.reply("{}").reply("{}").reply("{}")
                .reply("{\"reasoning\":\"fine now\",\"done\":true}");
        StructuredResult<Answer> result = caller(provider).call(structuredCall(), Answer.class, "answer",
                this::prompt, SemanticCheck.none(), new CancelToken(), AttemptObserver.NONE);
        assertThat(result.value().done()).isTrue();
        assertThat(result.strategy()).isEqualTo(OutputStrategy.JSON_OBJECT);
        assertThat(registry.get(endpoint(), "custom-chat").strategy()).isEqualTo(OutputStrategy.JSON_OBJECT);
    }

    /** v0.0.31 🍊 A valid strict answer clears the unreliability counter, so one bad reply is not punished. */
    @Test
    void oneInvalidStrictAnswerDoesNotDowngrade() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.reply("{}").reply("{\"reasoning\":\"ok\",\"done\":false}");
        StructuredResult<Answer> result = caller(provider).call(structuredCall(), Answer.class, "answer",
                this::prompt, SemanticCheck.none(), new CancelToken(), AttemptObserver.NONE);
        assertThat(result.strategy()).isEqualTo(OutputStrategy.JSON_SCHEMA_STRICT);
        assertThat(registry.get(endpoint(), "custom-chat").strictFailures()).isZero();
    }

    /**
     * v0.0.31 🍊 An endpoint that rejects every JSON format walks strict → json_object → prompt-only once
     * each and then reports the provider error, instead of downgrading forever.
     */
    @Test
    void downgradeChainIsExhausted() {
        ScriptedProvider provider = new ScriptedProvider();
        for (int i = 0; i < 10; i++) {
            provider.fail(() -> new ProviderRequestException(400,
                    "Invalid parameter: 'response_format' is not supported", "response_format", null));
        }
        assertThatThrownBy(() -> caller(provider).call(structuredCall(), Answer.class, "answer", this::prompt,
                SemanticCheck.none(), new CancelToken(), AttemptObserver.NONE))
                .isInstanceOf(ProviderRequestException.class);
        assertThat(registry.get(endpoint(), "custom-chat").strategy()).isEqualTo(OutputStrategy.PROMPT_ONLY);
        assertThat(provider.requests()).as("one attempt per strategy, then stop").hasSize(3);
        assertThat(provider.requests().get(0).format()).isInstanceOf(ResponseFormat.JsonSchema.class);
        assertThat(provider.requests().get(1).format()).isInstanceOf(ResponseFormat.JsonObject.class);
        assertThat(provider.requests().get(2).format()).isInstanceOf(ResponseFormat.None.class);
    }

    /** v0.0.31 🍊 A downgrade that keeps being signalled is capped, so the caller cannot spin forever. */
    @Test
    void repeatedDowngradeSignalsAreCapped() {
        ScriptedProvider provider = new ScriptedProvider();
        for (int i = 0; i < 10; i++) {
            provider.fail(() -> new StrategyDowngradedException(OutputStrategy.JSON_OBJECT, null));
        }
        assertThatThrownBy(() -> caller(provider).call(structuredCall(), Answer.class, "answer", this::prompt,
                SemanticCheck.none(), new CancelToken(), AttemptObserver.NONE))
                .isInstanceOf(StrategyDowngradedException.class);
        assertThat(provider.requests()).as("MAX_DOWNGRADES attempts plus the one that gives up").hasSize(4);
    }

    /** v0.0.31 🍊 Validation retries are exhausted with the schema name and the errors attached to the failure. */
    @Test
    void validationExhaustionCarriesDiagnostics() {
        ScriptedProvider provider = new ScriptedProvider();
        for (int i = 0; i < 5; i++) {
            provider.reply("still not json");
        }
        assertThatThrownBy(() -> caller(provider).call(structuredCall(), Answer.class, "answer", this::prompt,
                SemanticCheck.none(), new CancelToken(), AttemptObserver.NONE))
                .isInstanceOf(LlmOutputInvalidException.class)
                .satisfies(e -> {
                    LlmOutputInvalidException invalid = (LlmOutputInvalidException) e;
                    assertThat(invalid.details()).containsEntry("schema", "answer").containsEntry("attempts", 4);
                    assertThat(invalid.details().get("errors").toString()).contains("JSON");
                });
        assertThat(provider.requests()).as("1 call + 3 retries").hasSize(4);
    }

    /** v0.0.31 🍊 A refusal is final: no retry, no downgrade, a clear failure for the module to degrade on. */
    @Test
    void refusalIsNotRetried() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.refuse("I cannot help with that.");
        assertThatThrownBy(() -> caller(provider).call(structuredCall(), Answer.class, "answer", this::prompt,
                SemanticCheck.none(), new CancelToken(), AttemptObserver.NONE))
                .isInstanceOf(LlmOutputInvalidException.class).hasMessageContaining("refused");
        assertThat(provider.requests()).hasSize(1);
    }

    /** v0.0.31 🍊 Fresh registry per test, backed by an empty setting store. */
    private CapabilityRegistry newRegistry() {
        AppSettingRepository store = mock(AppSettingRepository.class);
        when(store.get(anyString())).thenReturn(Optional.empty());
        return new CapabilityRegistry(store, new Jsons(mapper),
                new NaturalTime(Clock.systemUTC(), ZoneId.of("America/Los_Angeles")));
    }

    /** v0.0.31 🍊 Structured caller over a scripted provider. */
    private StructuredCaller caller(ChatProvider provider) {
        return new StructuredCaller(new LlmExecutor(provider, registry, sleeps::add), registry, schemas,
                new SchemaValidator(schemas), mapper);
    }

    /** v0.0.31 🍊 Base call for the structured tests. */
    private LlmCall structuredCall() {
        return new LlmCall(endpoint(), new TierSettings("custom-chat", null, 400), List.of(), null, null, null,
                false, null, null);
    }

    /** v0.0.31 🍊 Free-text call against the fake HTTP server. */
    private LlmCall httpCall(String model) {
        return new LlmCall(endpoint(), new TierSettings(model, null, 100), List.of(LlmMessage.user("hi")), null,
                null, null, false, null, null);
    }

    /** v0.0.31 🍊 Prompt built for a strategy (non-strict strategies carry the schema in S1). */
    private Prompt prompt(OutputStrategy strategy) {
        return PromptBuilder.start("HANDBOOK", "Answer.\n"
                        + SchemaInstructions.forStrategy(strategy, schemas.schemaText(Answer.class)))
                .add(SegmentRank.S7_STIMULUS, "Alice asked for help.")
                .build("Saturday, September 19, 2026 at 11:32:05 AM PDT");
    }

    /** v0.0.31 🍊 The endpoint of the fake server. */
    private ProviderEndpoint endpoint() {
        return new ProviderEndpoint(server.baseUrl(), "sk-test-key-0001");
    }

    /** v0.0.31 🍊 In-memory provider replaying scripted answers and failures. */
    static final class ScriptedProvider implements ChatProvider {

        private final Deque<Supplier<LlmResult>> script = new ArrayDeque<>();
        private final List<LlmRequest> requests = new ArrayList<>();

        /** v0.0.31 🍊 Queues a successful answer. */
        ScriptedProvider reply(String text) {
            script.add(() -> new LlmResult(text, "stop", null, new Usage(100, 0, 0, 10, 0, false, true),
                    "custom-chat", 5, null, "{}", "{}"));
            return this;
        }

        /** v0.0.31 🍊 Queues a refusal. */
        ScriptedProvider refuse(String reason) {
            script.add(() -> new LlmResult("", "stop", reason, new Usage(100, 0, 0, 1, 0, false, true),
                    "custom-chat", 5, null, "{}", "{}"));
            return this;
        }

        /** v0.0.31 🍊 Queues a failure. */
        ScriptedProvider fail(Supplier<RuntimeException> error) {
            script.add(() -> {
                throw error.get();
            });
            return this;
        }

        @Override
        public LlmResult complete(ProviderEndpoint endpoint, LlmRequest request, CancelToken cancel) {
            requests.add(request);
            Supplier<LlmResult> next = script.poll();
            if (next == null) {
                throw new IllegalStateException("the scripted provider ran out of answers");
            }
            return next.get();
        }

        @Override
        public LlmResult stream(ProviderEndpoint endpoint, LlmRequest request, StreamSink sink, CancelToken cancel) {
            return complete(endpoint, request, cancel);
        }

        /** v0.0.31 🍊 Requests received so far. */
        List<LlmRequest> requests() {
            return requests;
        }
    }
}
