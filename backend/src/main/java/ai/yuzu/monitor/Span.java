package ai.yuzu.monitor;

/** v0.0.12 🍊 Handle of one unit of module work: START when created, STATE updates, then exactly one terminal event; never throws. */
public interface Span extends AutoCloseable {

    /** v0.0.12 🍊 Trace this span belongs to (hand it to downstream work, pool messages and chat posts). */
    String traceId();

    /** v0.0.12 🍊 Id of this span (the parentSpanId of nested work). */
    String spanId();

    /** v0.0.12 🍊 Reports what the module is doing right now (STATE); ignored after the span finished. */
    Span state(String text);

    /** v0.0.12 🍊 Overrides the desk state this span implies (CHAT posting -> TALKING, waiting on a card -> WAITING, IDLE = quiet). */
    Span desk(DeskState desk);

    /** v0.0.12 🍊 Attaches a detail entry reported with the terminal event (END, ERROR or CANCELLED). */
    Span detail(String key, Object value);

    /** v0.0.12 🍊 Starts a nested span of the same agent in the same trace, with this span as its parent. */
    Span child(ModuleKind module, String text);

    /** v0.0.12 🍊 Finishes successfully (END with durationMs); later terminal calls are ignored. */
    void end(String text);

    /** v0.0.12 🍊 Finishes with a failure (ERROR); a CancelledException or InterruptedException becomes CANCELLED. */
    void fail(Throwable error);

    /** v0.0.12 🍊 Finishes with a failure described in words (ERROR). */
    void fail(String reason);

    /** v0.0.12 🍊 Finishes as cancelled (CANCELLED: pause, interrupt, superseded work). */
    void cancelled(String reason);

    /** v0.0.12 🍊 True once a terminal event was reported. */
    boolean finished();

    /** v0.0.12 🍊 Ends the span with "done" unless it already finished (try-with-resources friendly). */
    @Override
    void close();
}
