package ai.yuzu.llm;

import ai.yuzu.common.error.LlmTransportException;
import ai.yuzu.common.security.SecretScanner;
import ai.yuzu.llm.capability.CapabilityRegistry;
import ai.yuzu.llm.prompt.Prompt;
import ai.yuzu.llm.prompt.TokenEstimator;
import ai.yuzu.llm.provider.LlmMessage;
import ai.yuzu.llm.provider.LlmRequest;
import ai.yuzu.llm.provider.LlmResult;
import ai.yuzu.llm.provider.ProviderEndpoint;
import ai.yuzu.llm.provider.ResponseFormat;
import ai.yuzu.llm.provider.StreamSink;
import ai.yuzu.llm.structured.OutputStrategy;
import ai.yuzu.llm.structured.SemanticCheck;
import ai.yuzu.llm.structured.StructuredCaller;
import ai.yuzu.llm.structured.StructuredResult;
import ai.yuzu.llm.usage.TokenMeter;
import ai.yuzu.llm.usage.Usage;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.settings.TierSettings;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

/**
 * v0.0.16 🍊 The single entry point for every model call made by any module.
 *
 * <p>Resolves tier → model and the provider endpoint from the console settings, takes a
 * {@link PriorityGate} permit, runs the call (structured with validation + retries, or free text with
 * optional streaming), meters tokens/cache hits per agent/module/tier/model, records every HTTP attempt,
 * throttles background work after 429s, and redacts credentials from every outgoing message. Pure-function calls (same full prompt → same answer, e.g.
 * safety verdicts) can opt into a 10-minute local response cache; hits are metered separately.</p>
 */
@Component
public class LlmGateway {

    private final SettingsService settings;
    private final StructuredCaller structured;
    private final LlmExecutor executor;
    private final CapabilityRegistry capabilities;
    private final TokenMeter meter;
    private final LlmCallRecorder recorder;
    private final PriorityGate gate;
    private final TokenEstimator tokens;
    private final Cache<String, StructuredResult<?>> responseCache = Caffeine.newBuilder()
            .maximumSize(5_000).expireAfterWrite(Duration.ofMinutes(10)).build();

    /** v0.0.11 🍊 Injects collaborators. */
    public LlmGateway(SettingsService settings, StructuredCaller structured, LlmExecutor executor,
                      CapabilityRegistry capabilities, TokenMeter meter, LlmCallRecorder recorder, PriorityGate gate,
                      TokenEstimator tokens) {
        this.settings = settings;
        this.structured = structured;
        this.executor = executor;
        this.capabilities = capabilities;
        this.meter = meter;
        this.recorder = recorder;
        this.gate = gate;
        this.tokens = tokens;
    }

    /**
     * v0.0.11 🍊 Structured (JSON) call with validation, semantic checks and up to 3 format retries.
     *
     * @param cacheable true only for pure functions of the prompt (identical prompt → reuse the answer)
     */
    @SuppressWarnings("unchecked")
    public <T> StructuredResult<T> structured(LlmCallContext ctx, Class<T> type,
                                              Function<OutputStrategy, Prompt> promptFor, SemanticCheck<T> check,
                                              boolean cacheable) {
        TierSettings tier = settings.current().tier(ctx.tier());
        ProviderEndpoint endpoint = endpoint();
        TokenMeter.Key key = new TokenMeter.Key(ctx.agentId().value(), ctx.module(), ctx.tier().name(), tier.model());
        String cacheKey = null;
        if (cacheable) {
            OutputStrategy current = capabilities.get(endpoint, tier.model()).strategy();
            cacheKey = digest(tier.model(), type.getName(), promptFor.apply(current));
            StructuredResult<?> hit = responseCache.getIfPresent(cacheKey);
            meter.recordLocalCache(hit != null);
            if (hit != null) {
                return (StructuredResult<T>) hit;
            }
        }
        meter.recordCall(key);
        LlmCall base = new LlmCall(endpoint, tier, List.of(), null, null, ctx.promptCacheKey(), false, null,
                null);
        Function<OutputStrategy, Prompt> redacted = strategy -> redact(promptFor.apply(strategy));
        try (PriorityGate.Permit ignored = gate.acquire(ctx.module())) {
            StructuredResult<T> result = structured.call(base, type, ctx.module().toLowerCase(), redacted, check,
                    ctx.cancel(), observer(ctx, key, true));
            for (int i = 1; i < result.attempts(); i++) {
                meter.recordRetry(key);
            }
            if (cacheKey != null) {
                responseCache.put(cacheKey, result);
            }
            return result;
        }
    }

    /** v0.0.11 🍊 Free-text call; streams deltas to the sink when it is not null. */
    public LlmResult text(LlmCallContext ctx, Prompt prompt, StreamSink sink) {
        TierSettings tier = settings.current().tier(ctx.tier());
        TokenMeter.Key key = new TokenMeter.Key(ctx.agentId().value(), ctx.module(), ctx.tier().name(), tier.model());
        meter.recordCall(key);
        LlmCall call = new LlmCall(endpoint(), tier, redact(prompt).messages(), null, null, ctx.promptCacheKey(),
                sink != null, null, null);
        try (PriorityGate.Permit ignored = gate.acquire(ctx.module())) {
            return executor.execute(call, OutputStrategy.PROMPT_ONLY, sink, ctx.cancel(), observer(ctx, key, false));
        }
    }

    /** v0.0.16 🍊 Redacts credentials from every message so no secret ever reaches a model provider. */
    static Prompt redact(Prompt prompt) {
        List<LlmMessage> clean = prompt.messages().stream()
                .map(m -> new LlmMessage(m.role(), SecretScanner.redact(m.content()))).toList();
        return clean.equals(prompt.messages()) ? prompt : new Prompt(clean);
    }

    /** v0.0.11 🍊 Current provider endpoint with the decrypted key (NOT_CONFIGURED when no key is saved). */
    public ProviderEndpoint endpoint() {
        return new ProviderEndpoint(settings.current().baseUrl(), settings.requireApiKey());
    }

    /** v0.0.11 🍊 Observer that meters and records every HTTP attempt. */
    private AttemptObserver observer(LlmCallContext ctx, TokenMeter.Key key, boolean structuredCall) {
        return new AttemptObserver() {
            @Override
            public void onSuccess(LlmRequest request, LlmResult result, int attempt) {
                Usage usage = result.usage();
                if (usage.promptTokens() == 0 && usage.completionTokens() == 0) {
                    usage = estimate(request.messages(), result.text());
                }
                LlmResult metered = usage == result.usage() ? result : new LlmResult(result.text(),
                        result.finishReason(), result.refusal(), usage, result.model(), result.latencyMs(),
                        result.ttftMs(), result.requestJson(), result.responseJson());
                meter.recordAttempt(key, usage, result.latencyMs(), false);
                if (attempt > 1) {
                    meter.recordRetry(key);
                }
                recorder.success(ctx, strategyOf(request, structuredCall), request, metered, attempt);
            }

            @Override
            public void onFailure(LlmRequest request, RuntimeException error, long latencyMs, int attempt) {
                meter.recordAttempt(key, Usage.NONE, latencyMs, true);
                recorder.failure(ctx, strategyOf(request, structuredCall), request, error, latencyMs, attempt);
                if (error instanceof LlmTransportException t && t.status() == 429) {
                    gate.onRateLimited();
                }
            }
        };
    }

    /** v0.0.11 🍊 Local estimate when the provider reports no usage (marked estimated, excluded from hit rate). */
    private Usage estimate(List<LlmMessage> messages, String text) {
        return new Usage(tokens.count(messages), 0, 0, tokens.count(text), 0, true, false);
    }

    /** v0.0.11 🍊 Strategy label of a shaped request (for the llm_call row). */
    private static String strategyOf(LlmRequest request, boolean structuredCall) {
        return switch (request.format()) {
            case ResponseFormat.JsonSchema ignored -> OutputStrategy.JSON_SCHEMA_STRICT.name();
            case ResponseFormat.JsonObject ignored -> OutputStrategy.JSON_OBJECT.name();
            case ResponseFormat.None ignored -> structuredCall ? OutputStrategy.PROMPT_ONLY.name() : "TEXT";
        };
    }

    /** v0.0.11 🍊 SHA-256 of model + type + the full prompt (local response-cache key). */
    private static String digest(String model, String type, Prompt prompt) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update((model + " " + type).getBytes(StandardCharsets.UTF_8));
            for (LlmMessage m : prompt.messages()) {
                sha.update((" " + m.role() + " " + m.content()).getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
