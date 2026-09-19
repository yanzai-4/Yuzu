package ai.yuzu.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * v0.0.8 🍊 Scriptable OpenAI-compatible HTTP server for tests (JDK HttpServer, no extra dependency).
 *
 * <p>Queue responses with {@link #enqueue}; every received request body is recorded in order.</p>
 */
public final class FakeLlmServer implements AutoCloseable {

    /** v0.0.8 🍊 One scripted response. */
    public record Reply(int status, String body, String contentType, Map<String, String> headers) {
    }

    private final HttpServer server;
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<Map.Entry<String, Reply>> routed = new ArrayList<>();
    private final List<String> requests = new ArrayList<>();

    /** v0.0.8 🍊 Starts on a random local port. */
    public FakeLlmServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Reply reply;
            synchronized (this) {
                requests.add(body);
                reply = null;
                for (int i = 0; i < routed.size(); i++) {
                    if (body.contains(routed.get(i).getKey())) {
                        reply = routed.remove(i).getValue();
                        break;
                    }
                }
                if (reply == null) {
                    reply = replies.isEmpty() ? new Reply(500, "{\"error\":{\"message\":\"no scripted reply\"}}",
                            "application/json", Map.of()) : replies.poll();
                }
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", reply.contentType());
            reply.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    /** v0.0.8 🍊 Base URL like http://127.0.0.1:PORT/v1. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    /** v0.0.8 🍊 Queues a JSON reply. */
    public synchronized FakeLlmServer enqueue(int status, String json) {
        replies.add(new Reply(status, json, "application/json", Map.of()));
        return this;
    }

    /** v0.0.18 🍊 Queues a reply used only for a request whose body contains the marker (e.g. a schema name). */
    public synchronized FakeLlmServer enqueueFor(String marker, String json) {
        routed.add(Map.entry(marker, new Reply(200, json, "application/json", Map.of())));
        return this;
    }

    /** v0.0.18 🍊 Marker matching the strict schema name of a module ("behavior", "tool_calling", ...). */
    public static String schema(String name) {
        return "\"name\":\"" + name + "\"";
    }

    /** v0.0.8 🍊 Queues a reply with headers. */
    public synchronized FakeLlmServer enqueue(int status, String json, Map<String, String> headers) {
        replies.add(new Reply(status, json, "application/json", headers));
        return this;
    }

    /** v0.0.8 🍊 Queues a streamed (SSE) reply made of data lines. */
    public synchronized FakeLlmServer enqueueStream(List<String> dataLines) {
        StringBuilder sb = new StringBuilder();
        dataLines.forEach(line -> sb.append("data: ").append(line).append("\n\n"));
        sb.append("data: [DONE]\n\n");
        replies.add(new Reply(200, sb.toString(), "text/event-stream", Map.of()));
        return this;
    }

    /** v0.0.8 🍊 A chat completion JSON with content and usage. */
    public static String completion(String content, int prompt, int cached, int completion) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"model\":\"test-model\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + escaped + "\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":" + prompt
                + ",\"completion_tokens\":" + completion + ",\"prompt_tokens_details\":{\"cached_tokens\":" + cached
                + "}}}";
    }

    /** v0.0.8 🍊 Received request bodies in order. */
    public synchronized List<String> requests() {
        return List.copyOf(requests);
    }

    /** v0.0.8 🍊 Stops the server. */
    @Override
    public void close() {
        server.stop(0);
    }
}
