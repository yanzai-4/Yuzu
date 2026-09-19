package ai.yuzu.llm;

import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.config.YuzuProperties;
import ai.yuzu.llm.provider.LlmRequest;
import ai.yuzu.llm.provider.LlmResult;
import ai.yuzu.llm.usage.Usage;
import ai.yuzu.persistence.BatchWriter;
import ai.yuzu.persistence.BatchWriterFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * v0.0.11 🍊 Persists every HTTP attempt: one {@code llm_call} row (batched) plus the full request/response JSON
 * in the agent's workspace ({@code llm/<date>/<callId>.json}) for the trace inspector.
 *
 * <p>Nothing here blocks the calling agent: rows go through a {@link BatchWriter}, files are written on a
 * virtual thread. Payloads never contain the API key (it lives only in an HTTP header).</p>
 */
@Component
public class LlmCallRecorder {

    /** v0.0.11 🍊 One row of the llm_call table. */
    public record Row(String agentId, String id, String module, String tier, String model, String strategy, int attempt,
                      Usage usage, long latencyMs, Long ttftMs, String status, String error, String traceId,
                      String payloadPath, Instant createdAt) {
    }

    private final BatchWriter<Row> writer;
    private final ObjectMapper mapper;
    private final NaturalTime time;
    private final AsyncRunner runner;
    private final Path workspaceRoot;

    /** v0.0.11 🍊 Creates the batched writer for llm_call. */
    public LlmCallRecorder(BatchWriterFactory writers, ObjectMapper mapper, NaturalTime time, AsyncRunner runner,
                           YuzuProperties properties) {
        this.mapper = mapper;
        this.time = time;
        this.runner = runner;
        this.workspaceRoot = properties.workspaceRootPath();
        this.writer = writers.create("llm_call", """
                        INSERT INTO llm_call (agent_id, id, module, tier, model, strategy, attempt, prompt_tokens,
                            cached_tokens, cache_write_tokens, completion_tokens, reasoning_tokens, estimated,
                            latency_ms, ttft_ms, status, error, trace_id, payload_path, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                (ps, r) -> {
                    ps.setString(1, r.agentId());
                    ps.setString(2, r.id());
                    ps.setString(3, r.module());
                    ps.setString(4, r.tier());
                    ps.setString(5, truncate(r.model(), 120));
                    ps.setString(6, r.strategy());
                    ps.setInt(7, r.attempt());
                    ps.setInt(8, r.usage().promptTokens());
                    ps.setInt(9, r.usage().cachedTokens());
                    ps.setInt(10, r.usage().cacheWriteTokens());
                    ps.setInt(11, r.usage().completionTokens());
                    ps.setInt(12, r.usage().reasoningTokens());
                    ps.setBoolean(13, r.usage().estimated());
                    ps.setLong(14, r.latencyMs());
                    if (r.ttftMs() == null) {
                        ps.setNull(15, java.sql.Types.INTEGER);
                    } else {
                        ps.setLong(15, r.ttftMs());
                    }
                    ps.setString(16, r.status());
                    ps.setString(17, truncate(r.error(), 500));
                    ps.setString(18, r.traceId());
                    ps.setString(19, r.payloadPath());
                    ps.setTimestamp(20, Timestamp.valueOf(DbTime.toDb(r.createdAt())));
                });
    }

    /** v0.0.11 🍊 Records a successful attempt (row + payload file). */
    public void success(LlmCallContext ctx, String strategy, LlmRequest request, LlmResult result, int attempt) {
        String id = IdGen.recordId(DataName.LLM_CALL, ctx.agentId());
        String path = writePayload(ctx, id, result.requestJson(), result.responseJson());
        writer.offer(new Row(ctx.agentId().value(), id, ctx.module(), ctx.tier().name(), request.model(), strategy,
                attempt, result.usage(), result.latencyMs(), result.ttftMs(), "OK", null, ctx.traceId(), path,
                time.nowInstant()));
    }

    /** v0.0.11 🍊 Records a failed attempt (row only). */
    public void failure(LlmCallContext ctx, String strategy, LlmRequest request, RuntimeException error,
                        long latencyMs, int attempt) {
        String id = IdGen.recordId(DataName.LLM_CALL, ctx.agentId());
        writer.offer(new Row(ctx.agentId().value(), id, ctx.module(), ctx.tier().name(), request.model(), strategy,
                attempt, Usage.NONE, latencyMs, null, "ERROR", error.getClass().getSimpleName() + ": "
                + error.getMessage(), ctx.traceId(), null, time.nowInstant()));
    }

    /** v0.0.11 🍊 Flushes pending rows (tests). */
    public void flush() {
        writer.flush();
    }

    /** v0.0.11 🍊 Writes the request/response JSON asynchronously; returns the relative path. */
    private String writePayload(LlmCallContext ctx, String id, String requestJson, String responseJson) {
        String relative = "llm/" + LocalDate.now(ZoneOffset.UTC) + "/" + id + ".json";
        Path file = workspaceRoot.resolve(ctx.agentId().value()).resolve(relative);
        runner.run("llm-payload", ctx.agentId().value(), () -> {
            try {
                ObjectNode payload = mapper.createObjectNode();
                payload.set("request", mapper.readTree(requestJson == null ? "{}" : requestJson));
                payload.set("response", mapper.readTree(responseJson == null ? "{}" : responseJson));
                Files.createDirectories(file.getParent());
                Files.writeString(file, payload.toString());
            } catch (IOException e) {
                throw new IllegalStateException("Cannot write LLM payload " + file, e);
            }
        });
        return relative;
    }

    /** v0.0.11 🍊 Truncates a string for a bounded column. */
    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
