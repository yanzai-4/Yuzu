package ai.yuzu.llm;

import ai.yuzu.common.concurrent.CancelToken;
import ai.yuzu.common.error.LlmOutputInvalidException;
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
import ai.yuzu.llm.provider.ProviderEndpoint;
import ai.yuzu.llm.provider.ProviderRequestException;
import ai.yuzu.llm.provider.ResponseFormat;
import ai.yuzu.llm.provider.StreamSink;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.Nullable;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** v0.0.10 🍊 Structured output: schema generation, 3 retries, cache-safe retry layout, downgrade, length handling. */
class StructuredCallerTest {

    /** v0.0.10 🍊 Sample module output. */
    enum Mode { ACT, THINK, END }

    /** v0.0.10 🍊 Sample nested record. */
    record Action(String text, int priority) {
    }

    /** v0.0.10 🍊 Sample output record exercising every supported shape. */
    record Decision(@Desc("Short reasoning") String reasoning, Mode mode, List<Action> actions,
                    @Nullable String nextThought) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final ScriptedProvider provider = new ScriptedProvider();
    private CapabilityRegistry registry;
    private StructuredCaller caller;
    private StrictSchemaFactory schemas;
    private final ProviderEndpoint endpoint = new ProviderEndpoint("https://gateway.example.com/v1", "sk-test-000000");

    @BeforeEach
    void setUp() {
        AppSettingRepository store = mock(AppSettingRepository.class);
        when(store.get(anyString())).thenReturn(Optional.empty());
        registry = new CapabilityRegistry(store, new Jsons(mapper),
                new NaturalTime(Clock.systemUTC(), ZoneId.of("America/Los_Angeles")));
        schemas = new StrictSchemaFactory(mapper);
        caller = new StructuredCaller(new LlmExecutor(provider, registry, ms -> { }), registry, schemas,
                new SchemaValidator(schemas), mapper);
    }

    /** v0.0.10 🍊 Strict schema: ordered properties, all required, nullable union, enum, no extras (golden). */
    @Test
    void schemaIsStrictAndOrdered() {
        assertThat(schemas.schemaText(Decision.class)).isEqualTo(
                "{\"type\":\"object\",\"properties\":{"
                        + "\"reasoning\":{\"description\":\"Short reasoning\",\"type\":\"string\"},"
                        + "\"mode\":{\"type\":\"string\",\"enum\":[\"ACT\",\"THINK\",\"END\"]},"
                        + "\"actions\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
                        + "\"text\":{\"type\":\"string\"},\"priority\":{\"type\":\"integer\"}},"
                        + "\"required\":[\"text\",\"priority\"],\"additionalProperties\":false}},"
                        + "\"nextThought\":{\"type\":[\"string\",\"null\"]}},"
                        + "\"required\":[\"reasoning\",\"mode\",\"actions\",\"nextThought\"],\"additionalProperties\":false}");
    }

    /** v0.0.10 🍊 invalid, invalid, valid → value after 3 attempts; each prompt extends the previous one. */
    @Test
    void retriesUntilValidKeepingPrefix() {
        provider.reply("not json at all")
                .reply("{\"reasoning\":\"r\",\"mode\":\"JUMP\",\"actions\":[],\"nextThought\":null}")
                .reply("{\"reasoning\":\"r\",\"mode\":\"ACT\",\"actions\":[{\"text\":\"post\",\"priority\":1}],\"nextThought\":null}");
        StructuredResult<Decision> result = call(SemanticCheck.none());
        assertThat(result.value().mode()).isEqualTo(Mode.ACT);
        assertThat(result.attempts()).isEqualTo(3);
        List<List<LlmMessage>> sent = provider.messages();
        assertThat(sent).hasSize(3);
        assertThat(sent.get(1).subList(0, sent.get(0).size())).isEqualTo(sent.get(0));
        assertThat(sent.get(2).subList(0, sent.get(1).size())).isEqualTo(sent.get(1));
        assertThat(sent.get(2).getLast().content()).contains("invalid").contains("mode");
    }

    /** v0.0.10 🍊 Four invalid answers exhaust the 3 retries. */
    @Test
    void failsAfterThreeRetries() {
        for (int i = 0; i < 4; i++) {
            provider.reply("{}");
        }
        assertThatThrownBy(() -> call(SemanticCheck.none())).isInstanceOf(LlmOutputInvalidException.class)
                .hasMessageContaining("4 times");
        assertThat(provider.messages()).hasSize(4);
    }

    /** v0.0.10 🍊 Semantic errors are fed back like schema errors. */
    @Test
    void semanticChecksTriggerRetries() {
        provider.reply("{\"reasoning\":\"r\",\"mode\":\"ACT\",\"actions\":[],\"nextThought\":null}")
                .reply("{\"reasoning\":\"r\",\"mode\":\"END\",\"actions\":[],\"nextThought\":null}");
        StructuredResult<Decision> result = call(d -> d.mode() == Mode.ACT && d.actions().isEmpty()
                ? List.of("ACT needs at least one action.") : List.of());
        assertThat(result.value().mode()).isEqualTo(Mode.END);
        assertThat(provider.messages().get(1).getLast().content()).contains("ACT needs at least one action.");
    }

    /** v0.0.10 🍊 A rejected response_format downgrades to json_object with the schema in the prompt (no retry used). */
    @Test
    void downgradesStrategyWithoutUsingRetries() {
        provider.fail(() -> new ProviderRequestException(400, "response_format json_schema is not supported", "response_format", null))
                .reply("{\"reasoning\":\"r\",\"mode\":\"THINK\",\"actions\":[],\"nextThought\":\"next\"}");
        StructuredResult<Decision> result = call(SemanticCheck.none());
        assertThat(result.strategy()).isEqualTo(OutputStrategy.JSON_OBJECT);
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(provider.requests().get(1).format()).isInstanceOf(ResponseFormat.JsonObject.class);
        assertThat(provider.messages().get(1).getFirst().content()).contains("JSON Schema");
    }

    /** v0.0.10 🍊 finish_reason=length doubles the output budget for the next attempt. */
    @Test
    void lengthDoublesBudget() {
        provider.reply("{\"reasoning\":\"very long", "length")
                .reply("{\"reasoning\":\"r\",\"mode\":\"END\",\"actions\":[],\"nextThought\":null}");
        call(SemanticCheck.none());
        assertThat(provider.requests().get(1).maxOutputTokens()).isEqualTo(provider.requests().get(0).maxOutputTokens() * 2);
    }

    private StructuredResult<Decision> call(SemanticCheck<Decision> semantic) {
        LlmCall base = new LlmCall(endpoint, new TierSettings("test-model", null, 500), List.of(), null, null, null,
                false, null, null);
        return caller.call(base, Decision.class, "decision", strategy -> prompt(strategy), semantic,
                new CancelToken(), AttemptObserver.NONE);
    }

    private Prompt prompt(OutputStrategy strategy) {
        return PromptBuilder.start("HANDBOOK", "Decide.\n" + SchemaInstructions.forStrategy(strategy,
                        schemas.schemaText(Decision.class)))
                .add(SegmentRank.S7_STIMULUS, "Alice asked for help.")
                .build("Saturday, September 19, 2026 at 11:32:05 AM PDT");
    }

    /** v0.0.10 🍊 In-memory provider replaying scripted answers and recording requests. */
    static final class ScriptedProvider implements ChatProvider {
        private final Deque<Supplier<LlmResult>> script = new ArrayDeque<>();
        private final List<LlmRequest> requests = new ArrayList<>();

        ScriptedProvider reply(String text) {
            return reply(text, "stop");
        }

        ScriptedProvider reply(String text, String finish) {
            script.add(() -> new LlmResult(text, finish, null, new Usage(100, 0, 0, 10, 0, false, true), "test-model",
                    5, null, "{}", "{}"));
            return this;
        }

        ScriptedProvider fail(Supplier<RuntimeException> error) {
            script.add(() -> {
                throw error.get();
            });
            return this;
        }

        @Override
        public LlmResult complete(ProviderEndpoint endpoint, LlmRequest request, CancelToken cancel) {
            requests.add(request);
            return script.poll().get();
        }

        @Override
        public LlmResult stream(ProviderEndpoint endpoint, LlmRequest request, StreamSink sink, CancelToken cancel) {
            return complete(endpoint, request, cancel);
        }

        List<LlmRequest> requests() {
            return requests;
        }

        List<List<LlmMessage>> messages() {
            return requests.stream().map(LlmRequest::messages).toList();
        }
    }
}
