package ai.yuzu.trace;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.monitor.ModuleEventView;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** v0.0.12 🍊 REST access to the monitor history: an agent's module events and whole traces. */
@RestController
@RequestMapping("/api")
public class TraceController {

    private final TraceQueryService queries;
    private final LlmCallInspector llmCalls;

    /** v0.0.30 🍊 Injects the query service and the raw model-call inspector. */
    public TraceController(TraceQueryService queries, LlmCallInspector llmCalls) {
        this.queries = queries;
        this.llmCalls = llmCalls;
    }

    /** v0.0.26 🍊 Response header carrying the cursor of the next (older) page; absent when the history is exhausted. */
    public static final String NEXT_CURSOR_HEADER = "X-Next-Cursor";

    /**
     * v0.0.26 🍊 {@code GET /api/agents/{agentId}/events?cursor=&beforeSeq=&limit=100}: newest first (ModuleEvent[]).
     *
     * <p>A client pages with the opaque {@value #NEXT_CURSOR_HEADER} header of the previous response; {@code beforeSeq}
     * stays available for callers that already know the sequence number.</p>
     */
    @GetMapping("/agents/{agentId}/events")
    public ResponseEntity<List<ModuleEventView>> agentEvents(@PathVariable String agentId,
                                                             @RequestParam(required = false) String cursor,
                                                             @RequestParam(required = false) Long beforeSeq,
                                                             @RequestParam(defaultValue = "100") int limit) {
        Long before = cursor == null || cursor.isBlank() ? beforeSeq : Long.valueOf(Cursors.decode(cursor));
        TraceQueryService.EventPage page = queries.agentEventPage(AgentId.of(agentId), before, limit);
        BodyBuilder response = ResponseEntity.ok();
        if (page.nextCursor() != null) {
            response = response.header(NEXT_CURSOR_HEADER, page.nextCursor());
        }
        return response.body(page.events());
    }

    /** v0.0.12 🍊 {@code GET /api/traces/{traceId}}: every event of the trace in time order, any agent. */
    @GetMapping("/traces/{traceId}")
    public List<ModuleEventView> trace(@PathVariable String traceId) {
        return queries.trace(traceId);
    }

    /** v0.0.30 🍊 {@code GET /api/traces/{traceId}/llm-calls}: the model calls of the trace, oldest first. */
    @GetMapping("/traces/{traceId}/llm-calls")
    public List<LlmCallView> traceLlmCalls(@PathVariable String traceId) {
        return llmCalls.ofTrace(traceId);
    }

    /** v0.0.30 🍊 {@code GET /api/llm-calls/{callId}/payload}: the exact request and response JSON of one call. */
    @GetMapping("/llm-calls/{callId}/payload")
    public LlmCallPayloadView llmCallPayload(@PathVariable String callId) {
        return llmCalls.payload(callId);
    }
}
