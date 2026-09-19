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

    /** v0.0.12 🍊 Injects the query service. */
    public TraceController(TraceQueryService queries) {
        this.queries = queries;
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
}
