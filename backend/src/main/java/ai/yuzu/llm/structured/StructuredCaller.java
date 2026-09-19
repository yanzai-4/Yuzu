package ai.yuzu.llm.structured;

import ai.yuzu.common.concurrent.CancelToken;
import ai.yuzu.common.error.LlmOutputInvalidException;
import ai.yuzu.llm.AttemptObserver;
import ai.yuzu.llm.LlmCall;
import ai.yuzu.llm.LlmExecutor;
import ai.yuzu.llm.StrategyDowngradedException;
import ai.yuzu.llm.capability.CapabilityRegistry;
import ai.yuzu.llm.prompt.Prompt;
import ai.yuzu.llm.provider.LlmResult;
import ai.yuzu.settings.TierSettings;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * v0.0.10 🍊 Guarantees a schema-valid, semantically valid JSON answer: 1 call + up to 3 format retries.
 *
 * <p>Per attempt: extract JSON → parse → validate against the exact schema → map to the record → run the
 * module's {@link SemanticCheck}. Retry feedback (the invalid answer + the error list) is appended at the
 * END of the prompt so the cached prefix is untouched. {@code finish_reason=length} doubles the token
 * budget; refusals are not retried. When the provider rejects the JSON format the strategy is downgraded
 * (strict → json_object → prompt-only), the prompt is rebuilt for it, and that does not count as a retry.</p>
 */
@Component
public class StructuredCaller {

    /** v0.0.10 🍊 Format retries after the first attempt. */
    public static final int MAX_RETRIES = 3;
    private static final int MAX_DOWNGRADES = 3;

    private final LlmExecutor executor;
    private final CapabilityRegistry capabilities;
    private final StrictSchemaFactory schemas;
    private final SchemaValidator validator;
    private final ObjectMapper mapper;

    /** v0.0.10 🍊 Injects collaborators. */
    public StructuredCaller(LlmExecutor executor, CapabilityRegistry capabilities, StrictSchemaFactory schemas,
                            SchemaValidator validator, ObjectMapper mapper) {
        this.executor = executor;
        this.capabilities = capabilities;
        this.schemas = schemas;
        this.validator = validator;
        this.mapper = mapper;
    }

    /**
     * v0.0.10 🍊 Runs a structured call.
     *
     * @param base       endpoint, tier, cache key, temperature and extra (its messages are ignored)
     * @param type       output record type
     * @param schemaName short schema name sent to the provider (the module name)
     * @param promptFor  builds the prompt for a strategy (non-strict strategies need the schema in S1)
     * @param semantic   module-specific checks
     */
    public <T> StructuredResult<T> call(LlmCall base, Class<T> type, String schemaName,
                                        Function<OutputStrategy, Prompt> promptFor, SemanticCheck<T> semantic,
                                        CancelToken cancel, AttemptObserver observer) {
        String model = base.tier().model();
        OutputStrategy strategy = capabilities.get(base.endpoint(), model).strategy();
        Prompt prompt = promptFor.apply(strategy);
        TierSettings tier = base.tier();
        int failures = 0;
        int downgrades = 0;
        while (true) {
            LlmCall call = new LlmCall(base.endpoint(), tier, prompt.messages(), schemaName, schemas.schemaFor(type),
                    base.promptCacheKey(), false, base.temperature(), base.extra());
            LlmResult result;
            try {
                result = executor.execute(call, strategy, null, cancel, observer);
            } catch (StrategyDowngradedException e) {
                if (++downgrades > MAX_DOWNGRADES) {
                    throw e;
                }
                strategy = e.next();
                prompt = promptFor.apply(strategy);
                continue;
            }
            if (result.refusal() != null && !result.refusal().isBlank()) {
                throw new LlmOutputInvalidException("The model refused: " + result.refusal())
                        .with("schema", schemaName);
            }
            if ("content_filter".equals(result.finishReason())) {
                throw new LlmOutputInvalidException(
                        "The provider's content filter blocked the answer.").with("schema", schemaName);
            }
            Attempt<T> attempt = check(type, result, semantic);
            if (strategy == OutputStrategy.JSON_SCHEMA_STRICT && !"length".equals(result.finishReason())) {
                capabilities.recordStrictOutcome(base.endpoint(), model, attempt.schemaValid());
            }
            if (attempt.errors().isEmpty()) {
                return new StructuredResult<>(attempt.value(), failures + 1, strategy, result);
            }
            failures++;
            if (failures > MAX_RETRIES) {
                throw new LlmOutputInvalidException(
                        "The model returned invalid " + schemaName + " output " + failures + " times.")
                        .with("schema", schemaName).with("attempts", failures).with("errors", attempt.errors());
            }
            if ("length".equals(result.finishReason())) {
                tier = new TierSettings(tier.model(), tier.reasoningEffort(), tier.maxOutputTokens() * 2);
            }
            prompt = prompt.withRetry(result.text(), feedback(attempt.errors()));
        }
    }

    /** v0.0.10 🍊 Parses, validates and semantically checks one answer. */
    private <T> Attempt<T> check(Class<T> type, LlmResult result, SemanticCheck<T> semantic) {
        List<String> errors = new ArrayList<>();
        if ("length".equals(result.finishReason())) {
            errors.add("Your answer was cut off because it was too long. Answer again, more concisely.");
            return new Attempt<>(null, errors, false);
        }
        String json = JsonExtractor.extract(result.text());
        if (json == null) {
            errors.add("The reply did not contain a JSON object.");
            return new Attempt<>(null, errors, false);
        }
        try {
            JsonNode node = mapper.readTree(json);
            List<String> schemaErrors = validator.validate(type, node);
            if (!schemaErrors.isEmpty()) {
                return new Attempt<>(null, schemaErrors, false);
            }
            T value = mapper.treeToValue(node, type);
            errors.addAll(semantic.errors(value));
            return new Attempt<>(value, errors, true);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            errors.add("The reply is not valid JSON for the schema: " + e.getMessage().lines().findFirst().orElse(""));
            return new Attempt<>(null, errors, false);
        }
    }

    /** v0.0.10 🍊 Retry feedback message (appended at the end of the prompt). */
    private static String feedback(List<String> errors) {
        StringBuilder sb = new StringBuilder("Your previous reply was invalid:\n");
        errors.stream().limit(12).forEach(e -> sb.append("- ").append(e).append('\n'));
        return sb.append("Reply again with ONLY the corrected JSON object.").toString();
    }

    /** v0.0.10 🍊 Outcome of checking one answer. */
    private record Attempt<T>(T value, List<String> errors, boolean schemaValid) {
    }
}
