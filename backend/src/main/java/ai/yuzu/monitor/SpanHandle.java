package ai.yuzu.monitor;

import ai.yuzu.common.error.CancelledException;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.common.id.AgentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/** v0.0.12 🍊 Live span: reports START/STATE and one terminal event through its emitter, times itself and never throws. */
final class SpanHandle implements Span {

    private static final Logger log = LoggerFactory.getLogger(SpanHandle.class);
    // Module details are capped so durationMs, errorType and errorCode always fit the sanitizer's entry limit.
    private static final int MAX_DETAILS = DetailSanitizer.MAX_ENTRIES - 3;

    private final SpanEmitter emitter;
    private final AgentId agentId;
    private final ModuleKind module;
    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final long startedNanos = System.nanoTime();
    // One lock serializes every emission of this span, so no STATE or desk change can follow its terminal event.
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Object> details = new LinkedHashMap<>();
    private volatile boolean finished;
    private volatile DeskState desk;
    private volatile String lastText = "";

    /** v0.0.12 🍊 Creates the handle; {@link #start(String)} reports the START event. */
    SpanHandle(SpanEmitter emitter, AgentId agentId, ModuleKind module, String traceId, String spanId,
               String parentSpanId) {
        this.emitter = emitter;
        this.agentId = agentId;
        this.module = module;
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.desk = module.desk();
    }

    /** v0.0.12 🍊 Reports the START event. */
    void start(String text) {
        lock.lock();
        try {
            lastText = text == null ? "" : text;
            emit(EventPhase.START, text, null);
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Owning agent. */
    AgentId agentId() {
        return agentId;
    }

    /** v0.0.12 🍊 Reporting module. */
    ModuleKind module() {
        return module;
    }

    /** v0.0.12 🍊 Parent span id, or null for a root span. */
    String parentSpanId() {
        return parentSpanId;
    }

    /** v0.0.12 🍊 Desk state the span currently declares. */
    DeskState currentDesk() {
        return desk;
    }

    /** v0.0.12 🍊 Latest START/STATE text. */
    String lastText() {
        return lastText;
    }

    /** v0.0.12 🍊 Trace id. */
    @Override
    public String traceId() {
        return traceId;
    }

    /** v0.0.12 🍊 Span id. */
    @Override
    public String spanId() {
        return spanId;
    }

    /** v0.0.12 🍊 Reports a STATE event unless the span already finished. */
    @Override
    public Span state(String text) {
        lock.lock();
        try {
            if (!finished) {
                lastText = text == null ? "" : text;
                emit(EventPhase.STATE, text, null);
            }
        } finally {
            lock.unlock();
        }
        return this;
    }

    /** v0.0.12 🍊 Declares another desk state (PAUSED and ERROR are ignored: only the board derives them). */
    @Override
    public Span desk(DeskState next) {
        if (next == null || !next.declarable()) {
            return this;
        }
        lock.lock();
        try {
            if (finished || next == desk) {
                return this;
            }
            desk = next;
            emitter.deskChanged(this);
        } catch (Throwable t) {
            log.warn("Monitor could not change the desk state of {} span {} ({})", module, spanId, agentId, t);
        } finally {
            lock.unlock();
        }
        return this;
    }

    /** v0.0.12 🍊 Captures a sanitized copy of the value for the terminal event (at most 29 keys per span). */
    @Override
    public Span detail(String key, Object value) {
        if (key == null) {
            return this;
        }
        try {
            Object safe = DetailSanitizer.value(value);
            lock.lock();
            try {
                if (!finished && (details.size() < MAX_DETAILS || details.containsKey(key))) {
                    details.put(key, safe);
                }
            } finally {
                lock.unlock();
            }
        } catch (Throwable t) {
            log.warn("Monitor could not capture detail {} of {} span {} ({})", key, module, spanId, agentId, t);
        }
        return this;
    }

    /** v0.0.12 🍊 Starts a nested span; falls back to a detached no-op span if that fails. */
    @Override
    public Span child(ModuleKind childModule, String text) {
        try {
            return emitter.startChild(this, childModule, text);
        } catch (Throwable t) {
            log.warn("Monitor could not start a child span of {} ({})", spanId, agentId, t);
            return NoopSpan.of(traceId, agentId);
        }
    }

    /** v0.0.12 🍊 END with the collected details and durationMs. */
    @Override
    public void end(String text) {
        finish(EventPhase.END, text, null);
    }

    /** v0.0.12 🍊 ERROR describing the failure (type and code in detail); cancellations become CANCELLED. */
    @Override
    public void fail(Throwable error) {
        try {
            if (error instanceof CancelledException || error instanceof InterruptedException) {
                cancelled(error.getMessage());
                return;
            }
            Map<String, Object> extra = new LinkedHashMap<>();
            if (error != null) {
                extra.put("errorType", error.getClass().getSimpleName());
                if (error instanceof YuzuException ye) {
                    extra.put("errorCode", ye.code().name());
                }
            }
            finish(EventPhase.ERROR, describe(error), extra);
        } catch (Throwable t) {
            log.warn("Monitor could not describe the failure of {} span {} ({})", module, spanId, agentId, t);
            finish(EventPhase.ERROR, null, null);
        }
    }

    /** v0.0.12 🍊 ERROR with the given reason. */
    @Override
    public void fail(String reason) {
        finish(EventPhase.ERROR, reason, null);
    }

    /** v0.0.12 🍊 CANCELLED with the given reason. */
    @Override
    public void cancelled(String reason) {
        finish(EventPhase.CANCELLED, reason, null);
    }

    /** v0.0.12 🍊 True once a terminal event was reported. */
    @Override
    public boolean finished() {
        return finished;
    }

    /** v0.0.12 🍊 END "done" unless the span already finished. */
    @Override
    public void close() {
        if (!finished) {
            end("done");
        }
    }

    /** v0.0.12 🍊 Reports the single terminal event with details and durationMs (idempotent). */
    private void finish(EventPhase phase, String text, Map<String, Object> extra) {
        lock.lock();
        try {
            if (finished) {
                return;
            }
            finished = true;
            Map<String, Object> all = new LinkedHashMap<>(details);
            if (extra != null) {
                all.putAll(extra);
            }
            all.put("durationMs", (System.nanoTime() - startedNanos) / 1_000_000);
            emit(phase, text, all);
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Hands one event to the emitter; a failure is logged, never thrown to the module. */
    private void emit(EventPhase phase, String text, Map<String, Object> detail) {
        try {
            emitter.emit(this, phase, text, detail);
        } catch (Throwable t) {
            log.warn("Monitor could not report {} {} for {}", module, phase, agentId, t);
        }
    }

    /** v0.0.12 🍊 Error text: the message of expected failures, only the type of unexpected ones (no secrets leak). */
    private static String describe(Throwable error) {
        if (error == null) {
            return EventPhase.ERROR.defaultText();
        }
        if (error instanceof YuzuException) {
            return "Failed: " + error.getMessage();
        }
        return "Failed unexpectedly (" + error.getClass().getSimpleName() + ")";
    }
}
