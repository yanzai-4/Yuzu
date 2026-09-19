package ai.yuzu.llm;

import ai.yuzu.common.concurrent.CancelToken;
import ai.yuzu.common.error.CancelledException;
import ai.yuzu.common.error.LlmTransportException;
import ai.yuzu.llm.capability.CapabilityRegistry;
import ai.yuzu.llm.capability.ModelCapabilities;
import ai.yuzu.llm.provider.ChatProvider;
import ai.yuzu.llm.provider.LlmRequest;
import ai.yuzu.llm.provider.LlmResult;
import ai.yuzu.llm.provider.ProviderRequestException;
import ai.yuzu.llm.provider.ResponseFormat;
import ai.yuzu.llm.provider.StreamSink;
import ai.yuzu.llm.structured.OutputStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * v0.0.8 🍊 Runs one logical model call: shapes the request by learned capabilities, adapts to rejected
 * parameters, and retries transport failures with jittered exponential backoff.
 *
 * <p>Budgets: up to {@value #MAX_LEARN_STEPS} free "learn and resend" steps (unsupported parameters) and up
 * to {@value #MAX_TRANSPORT_RETRIES} transport retries (429, 5xx, timeouts, I/O), honoring Retry-After. A
 * rejected JSON format downgrades the strategy and surfaces as {@link StrategyDowngradedException} so the
 * caller can rebuild its prompt. Format validation retries are NOT handled here.</p>
 */
@Component
public class LlmExecutor {

    static final int MAX_TRANSPORT_RETRIES = 3;
    static final int MAX_LEARN_STEPS = 5;
    private static final long MAX_BACKOFF_MILLIS = 30_000;

    private final ChatProvider provider;
    private final CapabilityRegistry capabilities;
    private final Sleeper sleeper;

    /** v0.0.11 🍊 Production constructor (the one Spring uses). */
    @Autowired
    public LlmExecutor(ChatProvider provider, CapabilityRegistry capabilities) {
        this(provider, capabilities, Thread::sleep);
    }

    /** v0.0.8 🍊 Test constructor with a controllable sleeper. */
    LlmExecutor(ChatProvider provider, CapabilityRegistry capabilities, Sleeper sleeper) {
        this.provider = provider;
        this.capabilities = capabilities;
        this.sleeper = sleeper;
    }

    /**
     * v0.0.8 🍊 Executes the call.
     *
     * @param strategy JSON strategy the caller built its prompt for (ignored for free text)
     * @param sink     stream sink (only used when {@code call.stream()} is true)
     */
    public LlmResult execute(LlmCall call, OutputStrategy strategy, StreamSink sink, CancelToken cancel,
                             AttemptObserver observer) {
        int learnSteps = 0;
        int transportRetries = 0;
        int attempt = 0;
        // Cooperative cancellation: an interrupted stream stops at the next delta (the blocking read is
        // aborted by the token's thread interrupt), so "stop talking" takes effect in milliseconds.
        StreamSink guarded = StreamSink.guarded(sink, cancel);
        while (true) {
            cancel.throwIfCancelled();
            ModelCapabilities caps = capabilities.get(call.endpoint(), call.tier().model());
            if (call.structured() && caps.strategy() != strategy && caps.strategy().ordinal() > strategy.ordinal()) {
                throw new StrategyDowngradedException(caps.strategy(), null);
            }
            LlmRequest request = shape(call, caps, strategy);
            attempt++;
            long started = System.nanoTime();
            try {
                LlmResult result = call.stream()
                        ? provider.stream(call.endpoint(), request, guarded, cancel)
                        : provider.complete(call.endpoint(), request, cancel);
                observer.onSuccess(request, result, attempt);
                return result;
            } catch (ProviderRequestException e) {
                observer.onFailure(request, e, elapsed(started), attempt);
                boolean formatError = call.structured() && CapabilityRegistry.isFormatError(e);
                if (learnSteps < MAX_LEARN_STEPS && capabilities.learn(call.endpoint(), call.tier().model(), e)) {
                    learnSteps++;
                    if (formatError) {
                        throw new StrategyDowngradedException(
                                capabilities.get(call.endpoint(), call.tier().model()).strategy(), e);
                    }
                    continue;
                }
                throw e;
            } catch (LlmTransportException e) {
                observer.onFailure(request, e, elapsed(started), attempt);
                if (!e.retryable() || transportRetries >= MAX_TRANSPORT_RETRIES) {
                    throw e;
                }
                backoff(transportRetries++, e.retryAfterMillis(), cancel);
            }
        }
    }

    /** v0.0.8 🍊 Applies capabilities: which token field, temperature, effort, format, cache key, stream usage. */
    LlmRequest shape(LlmCall call, ModelCapabilities caps, OutputStrategy strategy) {
        ResponseFormat format = ResponseFormat.NONE;
        if (call.structured()) {
            format = switch (strategy) {
                case JSON_SCHEMA_STRICT -> new ResponseFormat.JsonSchema(call.schemaName(), call.schema());
                case JSON_OBJECT -> ResponseFormat.JSON_OBJECT;
                case PROMPT_ONLY -> ResponseFormat.NONE;
            };
        }
        return new LlmRequest(call.tier().model(), call.messages(), format, call.tier().maxOutputTokens(),
                caps.maxTokensField(),
                caps.temperature() ? call.temperature() : null,
                caps.reasoningEffort() ? call.tier().reasoningEffort() : null,
                caps.promptCacheKey() ? call.promptCacheKey() : null,
                call.stream(), call.stream() && caps.streamUsage(), call.extra());
    }

    /** v0.0.8 🍊 Sleeps 1 s, 2 s, 4 s (+ jitter), or the server's Retry-After when longer (max 30 s). */
    private void backoff(int retry, long retryAfterMillis, CancelToken cancel) {
        long base = 1_000L << retry;
        long wait = Math.min(MAX_BACKOFF_MILLIS,
                Math.max(base + ThreadLocalRandom.current().nextLong(250), retryAfterMillis));
        try (CancelToken.Binding ignored = cancel.bindCurrentThread()) {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancelledException(cancel.reason());
        }
    }

    /** v0.0.8 🍊 Milliseconds since a nanoTime mark. */
    private static long elapsed(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    /** v0.0.8 🍊 Sleep abstraction so tests do not wait for real backoffs. */
    @FunctionalInterface
    interface Sleeper {
        /** v0.0.8 🍊 Sleeps for the given milliseconds. */
        void sleep(long millis) throws InterruptedException;
    }
}
