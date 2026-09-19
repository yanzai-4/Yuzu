package ai.yuzu.trace;

import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.config.YuzuProperties;
import ai.yuzu.llm.LlmCallRecorder;
import ai.yuzu.monitor.TraceIds;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

/**
 * v0.0.30 🍊 The raw-request view of the Trace tab: which model calls a trace made, and the exact JSON of one.
 *
 * <p>Metadata comes from {@code llm_call}; the full request/response payload lives in the agent's workspace
 * ({@code llm/<date>/<callId>.json}, written by {@link LlmCallRecorder}) and is read on demand. The payload
 * never contains credentials — the API key only ever travels in an HTTP header.</p>
 */
@Service
public class LlmCallInspector {

    /** v0.0.30 🍊 Most attempts returned for one trace. */
    public static final int MAX_CALLS = 500;
    /** v0.0.30 🍊 Largest payload file served to the inspector (bigger ones are refused, not truncated). */
    public static final long MAX_PAYLOAD_BYTES = 4L * 1024 * 1024;

    private final LlmCallRepository repository;
    private final LlmCallRecorder recorder;
    private final ObjectMapper mapper;
    private final Path workspaceRoot;

    /** v0.0.30 🍊 Injects collaborators. */
    public LlmCallInspector(LlmCallRepository repository, LlmCallRecorder recorder, ObjectMapper mapper,
                            YuzuProperties properties) {
        this.repository = repository;
        this.recorder = recorder;
        this.mapper = mapper;
        this.workspaceRoot = properties.workspaceRootPath();
    }

    /** v0.0.30 🍊 Every model call of a trace, oldest first (BAD_REQUEST for a malformed id, [] when there is none). */
    public List<LlmCallView> ofTrace(String traceId) {
        if (!TraceIds.isValid(traceId)) {
            throw new BadRequestException("A trace id is 1-32 letters, digits, '.', '_', ':' or '-'.")
                    .with("traceId", traceId);
        }
        recorder.flush();
        return repository.byTrace(traceId, MAX_CALLS).stream().map(LlmCallRepository.Row::view).toList();
    }

    /** v0.0.30 🍊 The exact request and response JSON of one call (NOT_FOUND when it was never written). */
    public LlmCallPayloadView payload(String callId) {
        recorder.flush();
        LlmCallRepository.Row row = repository.byId(callId)
                .orElseThrow(() -> new NotFoundException("Unknown model call " + callId + "."));
        if (row.payloadPath() == null) {
            throw new NotFoundException("This attempt failed before a payload was written.")
                    .forAgent(row.view().agentId());
        }
        JsonNode payload = read(row.view().agentId(), row.payloadPath());
        return new LlmCallPayloadView(row.view().id(), row.view().agentId(), row.view().model(),
                payload.path("request"), payload.path("response"));
    }

    /** v0.0.30 🍊 Reads the payload file inside the agent's workspace (path traversal and huge files refused). */
    private JsonNode read(String agentId, String relative) {
        Path agentRoot = workspaceRoot.resolve(agentId).normalize();
        Path file = agentRoot.resolve(relative).normalize();
        if (!file.startsWith(agentRoot)) {
            throw new NotFoundException("This payload is not inside the agent's workspace.").forAgent(agentId);
        }
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new NotFoundException("The raw payload of this call is no longer on disk.").forAgent(agentId);
            }
            if (Files.size(file) > MAX_PAYLOAD_BYTES) {
                throw new NotFoundException("This payload is too large to inspect in the browser.")
                        .forAgent(agentId);
            }
            return mapper.readTree(Files.readString(file));
        } catch (IOException e) {
            throw (NotFoundException) new NotFoundException("The raw payload of this call could not be read.")
                    .forAgent(agentId);
        }
    }
}
