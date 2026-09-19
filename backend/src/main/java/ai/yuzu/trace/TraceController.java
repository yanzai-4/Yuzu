package ai.yuzu.trace;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.monitor.ModuleEventView;
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

    /** v0.0.12 🍊 {@code GET /api/agents/{agentId}/events?beforeSeq=&limit=100}: newest first (contract ModuleEvent[]). */
    @GetMapping("/agents/{agentId}/events")
    public List<ModuleEventView> agentEvents(@PathVariable String agentId,
                                             @RequestParam(required = false) Long beforeSeq,
                                             @RequestParam(defaultValue = "100") int limit) {
        return queries.agentEvents(AgentId.of(agentId), beforeSeq, limit);
    }

    /** v0.0.12 🍊 {@code GET /api/traces/{traceId}}: every event of the trace in time order, any agent. */
    @GetMapping("/traces/{traceId}")
    public List<ModuleEventView> trace(@PathVariable String traceId) {
        return queries.trace(traceId);
    }
}
