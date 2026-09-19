package ai.yuzu.llm.provider;

import ai.yuzu.common.concurrent.CancelToken;
import ai.yuzu.common.error.CancelledException;
import ai.yuzu.common.error.LlmAuthException;
import ai.yuzu.common.error.LlmTransportException;
import ai.yuzu.llm.usage.Usage;
import ai.yuzu.llm.usage.UsageNormalizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * v0.0.8 🍊 OpenAI-compatible Chat Completions client (OpenAI, EdgeOne Makers gateway, DeepSeek, Kimi, ...).
 *
 * <p>Exactly one HTTP exchange per call; retries and capability learning live in the executor. The request
 * is interruptible: the calling thread is bound to the {@link CancelToken}, and interrupting it aborts
 * {@code HttpClient.send}. Streaming responses are parsed line by line ({@code data:} / {@code [DONE]}),
 * with an idle watchdog that aborts a stalled stream.</p>
 */
@Component
public class OpenAiCompatibleProvider implements ChatProvider {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final long STREAM_IDLE_MILLIS = 45_000;

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final Duration headerTimeout;

    /** v0.0.11 🍊 Creates the client (HTTP/1.1 for gateway compatibility, virtual-thread executor). */
    @Autowired
    public OpenAiCompatibleProvider(ObjectMapper mapper) {
        this(mapper, Duration.ofSeconds(180));
    }

    /** v0.0.8 🍊 Test constructor with a custom response-header timeout. */
    public OpenAiCompatibleProvider(ObjectMapper mapper, Duration headerTimeout) {
        this.mapper = mapper;
        this.headerTimeout = headerTimeout;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    /** v0.0.8 🍊 Non-streaming completion. */
    @Override
    public LlmResult complete(ProviderEndpoint endpoint, LlmRequest request, CancelToken cancel) {
        String body = toJson(request);
        long started = System.nanoTime();
        try (CancelToken.Binding ignored = cancel.bindCurrentThread()) {
            cancel.throwIfCancelled();
            HttpResponse<String> response = http.send(httpRequest(endpoint, body),
                    HttpResponse.BodyHandlers.ofString());
            long latency = (System.nanoTime() - started) / 1_000_000;
            checkStatus(response.statusCode(), response.body(), response.headers());
            JsonNode root = mapper.readTree(response.body());
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");
            String text = message.path("content").isTextual() ? message.path("content").asText() : "";
            String refusal = message.path("refusal").isTextual() ? message.path("refusal").asText() : null;
            return new LlmResult(text, textOrNull(choice.path("finish_reason")), refusal,
                    UsageNormalizer.from(root.path("usage")), root.path("model").asText(request.model()), latency,
                    null, body, response.body());
        } catch (HttpTimeoutException e) {
            throw new LlmTransportException("The model provider timed out.", 0, true, 0, e);
        } catch (IOException e) {
            if (cancel.isCancelled() || Thread.currentThread().isInterrupted()) {
                throw new CancelledException(cancel.reason());
            }
            throw new LlmTransportException("Could not reach the model provider: " + e.getMessage(), 0, true, 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancelledException(cancel.reason());
        }
    }

    /** v0.0.8 🍊 Streaming completion; deltas go to the sink, the aggregated text and usage are returned. */
    @Override
    public LlmResult stream(ProviderEndpoint endpoint, LlmRequest request, StreamSink sink, CancelToken cancel) {
        String body = toJson(request);
        long started = System.nanoTime();
        AtomicLong lastActivity = new AtomicLong(System.nanoTime());
        AtomicBoolean stalled = new AtomicBoolean();
        Thread reader = Thread.currentThread();
        Thread watchdog = Thread.ofVirtual().start(() -> watch(reader, lastActivity, stalled));
        try (CancelToken.Binding ignored = cancel.bindCurrentThread()) {
            cancel.throwIfCancelled();
            HttpResponse<Stream<String>> response = http.send(httpRequest(endpoint, body),
                    HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                String errorBody;
                try (Stream<String> lines = response.body()) {
                    errorBody = String.join("\n", lines.toList());
                }
                checkStatus(response.statusCode(), errorBody, response.headers());
            }
            StringBuilder text = new StringBuilder();
            String finish = null;
            String refusal = null;
            Usage usage = Usage.NONE;
            Long ttft = null;
            String model = request.model();
            try (Stream<String> lines = response.body()) {
                Iterator<String> it = lines.iterator();
                while (it.hasNext()) {
                    String line = it.next();
                    lastActivity.set(System.nanoTime());
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    JsonNode chunk = mapper.readTree(data);
                    if (chunk.hasNonNull("model")) {
                        model = chunk.get("model").asText();
                    }
                    if (chunk.has("usage") && !chunk.get("usage").isNull()) {
                        usage = UsageNormalizer.from(chunk.get("usage"));
                    }
                    JsonNode choice = chunk.path("choices").path(0);
                    JsonNode delta = choice.path("delta");
                    if (delta.path("content").isTextual() && !delta.path("content").asText().isEmpty()) {
                        String piece = delta.path("content").asText();
                        if (ttft == null) {
                            ttft = (System.nanoTime() - started) / 1_000_000;
                        }
                        text.append(piece);
                        sink.onDelta(piece);
                    }
                    if (delta.path("refusal").isTextual()) {
                        refusal = (refusal == null ? "" : refusal) + delta.path("refusal").asText();
                    }
                    if (choice.hasNonNull("finish_reason")) {
                        finish = choice.get("finish_reason").asText();
                    }
                }
            }
            long latency = (System.nanoTime() - started) / 1_000_000;
            ObjectNode summary = mapper.createObjectNode().put("streamed", true).put("text", text.toString());
            summary.put("finish_reason", finish);
            return new LlmResult(text.toString(), finish, refusal, usage, model, latency, ttft, body,
                    summary.toString());
        } catch (HttpTimeoutException e) {
            throw new LlmTransportException("The model provider timed out.", 0, true, 0, e);
        } catch (IOException | UncheckedIOException e) {
            if (stalled.get()) {
                Thread.interrupted();
                throw new LlmTransportException("The model stream stalled for 45 seconds.", 0, true, 0, e);
            }
            if (cancel.isCancelled() || Thread.currentThread().isInterrupted()) {
                throw new CancelledException(cancel.reason());
            }
            throw new LlmTransportException("The model stream broke: " + e.getMessage(), 0, true, 0, e);
        } catch (InterruptedException e) {
            if (stalled.get()) {
                throw new LlmTransportException("The model stream stalled for 45 seconds.", 0, true, 0, e);
            }
            Thread.currentThread().interrupt();
            throw new CancelledException(cancel.reason());
        } finally {
            watchdog.interrupt();
        }
    }

    /** v0.0.8 🍊 Builds the JSON body from a shaped request. */
    public String toJson(LlmRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", request.model());
        ArrayNode messages = body.putArray("messages");
        for (LlmMessage m : request.messages()) {
            messages.addObject().put("role", m.role()).put("content", m.content());
        }
        body.put(request.maxTokensField(), request.maxOutputTokens());
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        if (request.reasoningEffort() != null) {
            body.put("reasoning_effort", request.reasoningEffort());
        }
        switch (request.format()) {
            case ResponseFormat.JsonObject ignored -> body.putObject("response_format").put("type", "json_object");
            case ResponseFormat.JsonSchema schema -> {
                ObjectNode format = body.putObject("response_format");
                format.put("type", "json_schema");
                ObjectNode spec = format.putObject("json_schema");
                spec.put("name", schema.name());
                spec.put("strict", true);
                spec.set("schema", schema.schema());
            }
            case ResponseFormat.None ignored -> {
            }
        }
        if (request.stream()) {
            body.put("stream", true);
            if (request.streamUsage()) {
                body.putObject("stream_options").put("include_usage", true);
            }
        }
        if (request.promptCacheKey() != null) {
            body.put("prompt_cache_key", request.promptCacheKey());
        }
        if (request.extra() != null) {
            body.setAll(request.extra());
        }
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize request", e);
        }
    }

    /** v0.0.8 🍊 Builds the POST /chat/completions request. */
    private HttpRequest httpRequest(ProviderEndpoint endpoint, String body) {
        return HttpRequest.newBuilder(URI.create(endpoint.baseUrl() + "/chat/completions"))
                .timeout(headerTimeout)
                .header("Authorization", "Bearer " + endpoint.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    /** v0.0.8 🍊 Maps error statuses to typed exceptions (auth, request, retryable transport). */
    private void checkStatus(int status, String body, HttpHeaders headers) {
        if (status < 400) {
            return;
        }
        String message = "HTTP " + status;
        String param = null;
        String code = null;
        try {
            JsonNode error = mapper.readTree(body == null ? "{}" : body).path("error");
            if (error.path("message").isTextual()) {
                message = error.path("message").asText();
            }
            param = error.path("param").isTextual() ? error.path("param").asText() : null;
            code = error.path("code").isTextual() ? error.path("code").asText() : null;
        } catch (IOException ignored) {
            // Non-JSON error body.
        }
        if (status == 401 || status == 403) {
            throw new LlmAuthException("The model provider rejected the API key: " + message);
        }
        if (status == 429 || status >= 500 || status == 408) {
            throw new LlmTransportException("The model provider is busy (HTTP " + status + "): " + message, status,
                    true, retryAfterMillis(headers), null);
        }
        throw new ProviderRequestException(status, message, param, code);
    }

    /** v0.0.8 🍊 Reads retry-after-ms or retry-after (seconds) headers. */
    private static long retryAfterMillis(HttpHeaders headers) {
        try {
            var ms = headers.firstValue("retry-after-ms");
            if (ms.isPresent()) {
                return (long) Double.parseDouble(ms.get());
            }
            var seconds = headers.firstValue("retry-after");
            if (seconds.isPresent()) {
                return (long) (Double.parseDouble(seconds.get()) * 1000);
            }
        } catch (NumberFormatException ignored) {
            // HTTP-date form; fall back to the default backoff.
        }
        return 0;
    }

    /** v0.0.8 🍊 Interrupts the reader when the stream has been silent for too long. */
    private static void watch(Thread reader, AtomicLong lastActivity, AtomicBoolean stalled) {
        try {
            while (true) {
                Thread.sleep(1_000);
                if ((System.nanoTime() - lastActivity.get()) / 1_000_000 > STREAM_IDLE_MILLIS) {
                    stalled.set(true);
                    reader.interrupt();
                    return;
                }
            }
        } catch (InterruptedException ignored) {
            // Stream finished.
        }
    }

    /** v0.0.8 🍊 Text of a node or null. */
    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : node.asText();
    }
}
