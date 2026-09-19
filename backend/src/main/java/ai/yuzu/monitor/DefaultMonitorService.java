package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Consumer;

/** v0.0.12 🍊 MonitorService: every event is queued for MySQL, applied to the status board and published as module.event. */
@Service
public class DefaultMonitorService implements MonitorService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMonitorService.class);

    private final Consumer<ModuleEvent> persist;
    private final AgentStatusBoard board;
    private final SseHub hub;
    private final NaturalTime time;
    private final ModuleEventFactory events;
    private final SpanEmitter emitter = new Emitter();

    /** v0.0.12 🍊 Spring constructor: events are persisted through the batched module_event store. */
    @Autowired
    public DefaultMonitorService(ModuleEventStore store, AgentStatusBoard board, SseHub hub, NaturalTime time) {
        this(store::offer, board, hub, time);
    }

    /** v0.0.12 🍊 Constructor with a custom persistence sink (unit tests run without MySQL). */
    DefaultMonitorService(Consumer<ModuleEvent> persist, AgentStatusBoard board, SseHub hub, NaturalTime time) {
        this.persist = persist;
        this.board = board;
        this.hub = hub;
        this.time = time;
        this.events = new ModuleEventFactory(time);
    }

    /** v0.0.12 🍊 Starts a span and reports START; any internal failure yields a detached span instead of an exception. */
    @Override
    public Span start(AgentId agentId, ModuleKind module, String text, String traceId, String parentSpanId) {
        try {
            if (agentId == null || module == null) {
                log.warn("Ignored a monitor span without agent or module ({} / {})", agentId, module);
                return NoopSpan.of(traceId, agentId);
            }
            SpanHandle span = new SpanHandle(emitter, agentId, module, TraceIds.cleanOrNew(traceId, agentId),
                    TraceIds.newSpanId(agentId), TraceIds.clean(parentSpanId));
            span.start(text);
            return span;
        } catch (Throwable t) {
            log.warn("Monitor could not start a {} span for {}", module, agentId, t);
            return NoopSpan.of(null, agentId);
        }
    }

    /** v0.0.12 🍊 Reports a one-off INFO event (no span id; parentSpanId links it under a span). */
    @Override
    public void info(AgentId agentId, ModuleKind module, String text, Map<String, ?> detail, String traceId,
                     String parentSpanId) {
        try {
            if (agentId == null || module == null) {
                log.warn("Ignored a monitor event without agent or module ({} / {})", agentId, module);
                return;
            }
            dispatch(events.create(agentId, module, EventPhase.INFO, text, detail, TraceIds.clean(traceId), null,
                    TraceIds.clean(parentSpanId)), null);
        } catch (Throwable t) {
            log.warn("Monitor could not report an INFO event of {} for {}", module, agentId, t);
        }
    }

    /** v0.0.12 🍊 Updates the pool size shown on the desk. */
    @Override
    public void setPoolSize(AgentId agentId, int size) {
        guard("pool size", () -> board.setPoolSize(agentId, size));
    }

    /** v0.0.12 🍊 Updates the pending batch count shown on the desk. */
    @Override
    public void setPendingBatches(AgentId agentId, int count) {
        guard("pending batches", () -> board.setPendingBatches(agentId, count));
    }

    /** v0.0.12 🍊 Updates the waiting flag and reason. */
    @Override
    public void setWaiting(AgentId agentId, boolean waiting, String reason) {
        guard("waiting", () -> board.setWaiting(agentId, waiting, reason));
    }

    /** v0.0.12 🍊 Updates the pause flag. */
    @Override
    public void setPaused(AgentId agentId, boolean paused) {
        guard("paused", () -> board.setPaused(agentId, paused));
    }

    /** v0.0.12 🍊 Persists, applies and publishes one event; each step is isolated so one failure never skips the others. */
    private void dispatch(ModuleEvent event, DeskState desk) {
        try {
            persist.accept(event);
        } catch (Throwable t) {
            log.warn("Could not queue module event {} for persistence", event.id(), t);
        }
        try {
            board.apply(event, desk);
        } catch (Throwable t) {
            log.warn("Could not apply module event {} to the status board", event.id(), t);
        }
        try {
            String room = board.roomOf(event.agentId());
            if (room != null) {
                hub.publish(room, EventType.MODULE_EVENT, event.agentId().value(), event.toView(time));
            } else {
                log.debug("Module event {} of unknown agent {} is stored but not streamed", event.id(),
                        event.agentId());
            }
        } catch (Throwable t) {
            log.warn("Could not publish module event {}", event.id(), t);
        }
    }

    /** v0.0.12 🍊 Runs a board update, logging instead of throwing. */
    private void guard(String what, Runnable update) {
        try {
            update.run();
        } catch (Throwable t) {
            log.warn("Monitor could not update {}", what, t);
        }
    }

    /** v0.0.12 🍊 Bridges span handles to the dispatcher without exposing it publicly. */
    private final class Emitter implements SpanEmitter {

        /** v0.0.12 🍊 Builds and dispatches one span event with the span's current desk state. */
        @Override
        public void emit(SpanHandle span, EventPhase phase, String text, Map<String, Object> detail) {
            dispatch(events.create(span.agentId(), span.module(), phase, text, detail, span.traceId(), span.spanId(),
                    span.parentSpanId()), span.currentDesk());
        }

        /** v0.0.12 🍊 Forwards a desk-state change to the board. */
        @Override
        public void deskChanged(SpanHandle span) {
            board.redesk(span.agentId(), span.spanId(), span.module(), span.currentDesk(), span.lastText());
        }

        /** v0.0.12 🍊 Starts a nested span in the parent's trace. */
        @Override
        public Span startChild(SpanHandle parent, ModuleKind module, String text) {
            return start(parent.agentId(), module, text, parent.traceId(), parent.spanId());
        }
    }
}
