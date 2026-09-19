package ai.yuzu.monitor;

import java.util.Map;

/** v0.0.12 🍊 Callback surface a live span reports through (implemented privately by DefaultMonitorService). */
interface SpanEmitter {

    /** v0.0.12 🍊 Reports one event of the span (detail may be null). */
    void emit(SpanHandle span, EventPhase phase, String text, Map<String, Object> detail);

    /** v0.0.12 🍊 Tells the status board that the span now declares another desk state. */
    void deskChanged(SpanHandle span);

    /** v0.0.12 🍊 Starts a nested span of the same agent in the same trace. */
    Span startChild(SpanHandle parent, ModuleKind module, String text);
}
