package ai.yuzu.trace;

import ai.yuzu.agent.AgentService;
import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.monitor.ModuleEventStore;
import ai.yuzu.monitor.ModuleEventView;
import ai.yuzu.monitor.TraceIds;
import org.springframework.stereotype.Service;

import java.util.List;

/** v0.0.12 🍊 Reads the monitor history: one agent's events (newest first, paged by seq) and whole traces (time order). */
@Service
public class TraceQueryService {

    /** v0.0.12 🍊 Default page size of an agent's event history. */
    public static final int DEFAULT_LIMIT = 100;
    /** v0.0.12 🍊 Largest page size of an agent's event history. */
    public static final int MAX_LIMIT = 500;
    /** v0.0.12 🍊 Most events returned for one trace. */
    public static final int MAX_TRACE_EVENTS = 2_000;

    private final AgentEventRepository agentEvents;
    private final TraceEventRepository traces;
    private final ModuleEventStore store;
    private final AgentService agents;
    private final NaturalTime time;

    /** v0.0.12 🍊 Injects collaborators. */
    public TraceQueryService(AgentEventRepository agentEvents, TraceEventRepository traces, ModuleEventStore store,
                             AgentService agents, NaturalTime time) {
        this.agentEvents = agentEvents;
        this.traces = traces;
        this.store = store;
        this.agents = agents;
        this.time = time;
    }

    /** v0.0.12 🍊 An agent's events newest first; {@code beforeSeq} pages to older ones (NOT_FOUND for unknown agents). */
    public List<ModuleEventView> agentEvents(AgentId agentId, Long beforeSeq, int limit) {
        return agentEventPage(agentId, beforeSeq, limit).events();
    }

    /**
     * v0.0.26 🍊 One page plus an opaque cursor for the next one, so an HTTP client never has to know about seq.
     *
     * <p>The body contract stays {@code ModuleEvent[]}; the cursor travels in a response header.</p>
     */
    public EventPage agentEventPage(AgentId agentId, Long beforeSeq, int limit) {
        agents.require(agentId);
        store.flush();
        int size = Math.max(1, Math.min(limit, MAX_LIMIT));
        AgentEventRepository.Page page = agentEvents.page(agentId, beforeSeq, size);
        List<ModuleEventView> views = page.events().stream().map(e -> e.toView(time)).toList();
        String cursor = views.size() < size || page.oldestSeq() == null ? null : Cursors.encode(page.oldestSeq());
        return new EventPage(views, cursor);
    }

    /** v0.0.26 🍊 A page of an agent's events and the cursor that fetches the next (older) page, null when exhausted. */
    public record EventPage(List<ModuleEventView> events, String nextCursor) {
    }

    /** v0.0.12 🍊 Every event of a trace across agents, in time order (BAD_REQUEST for a malformed id). */
    public List<ModuleEventView> trace(String traceId) {
        if (!TraceIds.isValid(traceId)) {
            throw new BadRequestException("A trace id is 1-32 letters, digits, '.', '_', ':' or '-'.")
                    .with("traceId", traceId);
        }
        store.flush();
        return traces.byTrace(traceId, MAX_TRACE_EVENTS).stream().map(e -> e.toView(time)).toList();
    }
}
