package ai.yuzu.llm.capability;

import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.provider.ProviderEndpoint;
import ai.yuzu.llm.provider.ProviderRequestException;
import ai.yuzu.llm.structured.OutputStrategy;
import ai.yuzu.settings.AppSettingRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v0.0.8 🍊 Knows which request parameters each (base URL, model) accepts.
 *
 * <p>Seeded rules: reasoning models ({@code o*}, {@code gpt-5*}) take no temperature and use
 * {@code max_completion_tokens}; only api.openai.com gets {@code prompt_cache_key}. Anything else is learned
 * from HTTP 400 answers (the offending parameter is switched off and the request resent) and persisted in
 * {@code app_setting} so the lesson survives restarts.</p>
 */
@Component
public class CapabilityRegistry {

    private static final Logger log = LoggerFactory.getLogger(CapabilityRegistry.class);
    private static final String STORE_KEY = "llm.capabilities";
    private static final Pattern REASONING_MODEL = Pattern.compile("^(o\\d|gpt-5)");
    private static final Pattern PARAM_IN_MESSAGE = Pattern.compile(
            "(temperature|max_completion_tokens|max_tokens|reasoning_effort|stream_options|prompt_cache_key"
                    + "|response_format|json_schema)");
    private static final int STRICT_FAILURE_LIMIT = 3;

    private final AppSettingRepository store;
    private final Jsons jsons;
    private final NaturalTime time;
    private final Map<String, ModelCapabilities> learned = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    /** v0.0.8 🍊 Injects persistence helpers. */
    public CapabilityRegistry(AppSettingRepository store, Jsons jsons, NaturalTime time) {
        this.store = store;
        this.jsons = jsons;
        this.time = time;
    }

    /** v0.0.8 🍊 Capabilities for a model at an endpoint (learned values override the seeded rules). */
    public ModelCapabilities get(ProviderEndpoint endpoint, String model) {
        ensureLoaded();
        return learned.getOrDefault(key(endpoint, model), seeded(endpoint, model));
    }

    /**
     * v0.0.8 🍊 Learns from a rejected request. Returns true when a parameter was switched off and resending
     * may succeed; false when the error is not about a parameter we can adapt.
     */
    public boolean learn(ProviderEndpoint endpoint, String model, ProviderRequestException error) {
        String param = error.param();
        String message = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        if (param == null) {
            Matcher m = PARAM_IN_MESSAGE.matcher(message);
            param = m.find() ? m.group(1) : null;
        }
        if (param == null) {
            return false;
        }
        ModelCapabilities current = get(endpoint, model);
        ModelCapabilities next = switch (param) {
            case "temperature" -> current.temperature() ? current.withTemperature(false) : null;
            case "max_tokens" -> "max_tokens".equals(current.maxTokensField())
                    ? current.withMaxTokensField("max_completion_tokens") : null;
            case "max_completion_tokens" -> "max_completion_tokens".equals(current.maxTokensField())
                    ? current.withMaxTokensField("max_tokens") : null;
            case "reasoning_effort" -> current.reasoningEffort() ? current.withReasoningEffort(false) : null;
            case "stream_options" -> current.streamUsage() ? current.withStreamUsage(false) : null;
            case "prompt_cache_key" -> current.promptCacheKey() ? current.withPromptCacheKey(false) : null;
            case "response_format", "json_schema" -> current.strategy() != OutputStrategy.PROMPT_ONLY
                    ? current.withStrategy(current.strategy().weaker()) : null;
            default -> null;
        };
        if (next == null) {
            return false;
        }
        remember(endpoint, model, next);
        log.info("🍊 Learned capability for {} {}: {} -> {}", endpoint.host(), model, param, next);
        return true;
    }

    /** v0.0.8 🍊 True when the error means the requested JSON format itself is unsupported. */
    public static boolean isFormatError(ProviderRequestException error) {
        String text = (error.param() + " " + error.getMessage()).toLowerCase(Locale.ROOT);
        return text.contains("response_format") || text.contains("json_schema");
    }

    /** v0.0.8 🍊 Records a strict-schema validation outcome; downgrades after repeated silent failures. */
    public void recordStrictOutcome(ProviderEndpoint endpoint, String model, boolean valid) {
        ModelCapabilities current = get(endpoint, model);
        if (current.strategy() != OutputStrategy.JSON_SCHEMA_STRICT) {
            return;
        }
        if (valid) {
            if (current.strictFailures() != 0) {
                remember(endpoint, model, current.withStrictFailures(0));
            }
            return;
        }
        int failures = current.strictFailures() + 1;
        remember(endpoint, model, failures >= STRICT_FAILURE_LIMIT
                ? current.withStrategy(OutputStrategy.JSON_OBJECT) : current.withStrictFailures(failures));
    }

    /** v0.0.8 🍊 Forgets everything learned (used when the provider changes). */
    public void reset() {
        learned.clear();
        store.put(STORE_KEY, "{}", time.nowInstant());
    }

    /** v0.0.8 🍊 Seeded rules for a model that has not been learned yet. */
    public ModelCapabilities seeded(ProviderEndpoint endpoint, String model) {
        boolean openai = "api.openai.com".equals(endpoint.host());
        String bare = model.toLowerCase(Locale.ROOT).replaceFirst("^(openai/|@makers/)", "");
        boolean reasoning = REASONING_MODEL.matcher(bare).find();
        return new ModelCapabilities(!reasoning, reasoning || openai ? "max_completion_tokens" : "max_tokens",
                reasoning, true, openai, OutputStrategy.JSON_SCHEMA_STRICT, 0);
    }

    /** v0.0.8 🍊 Stores a learned value in memory and in app_setting. */
    private void remember(ProviderEndpoint endpoint, String model, ModelCapabilities value) {
        learned.put(key(endpoint, model), value);
        store.put(STORE_KEY, jsons.write(learned), time.nowInstant());
    }

    /** v0.0.8 🍊 Loads persisted lessons once. */
    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronizedLoad();
    }

    /** v0.0.8 🍊 Reads the persisted map (idempotent; races only re-read the same data). */
    private void synchronizedLoad() {
        store.get(STORE_KEY).ifPresent(json -> learned.putAll(
                jsons.read(json, new TypeReference<Map<String, ModelCapabilities>>() {
                })));
        loaded = true;
    }

    /** v0.0.8 🍊 Map key "baseUrl|model". */
    private static String key(ProviderEndpoint endpoint, String model) {
        return endpoint.baseUrl() + "|" + model;
    }
}
