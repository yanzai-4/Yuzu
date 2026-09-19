package ai.yuzu.module;

/**
 * v0.0.14 🍊 A running unit of module work as seen by the monitor (start → state* → end/fail/cancelled).
 *
 * <p>Implementations must never throw: reporting can never break an agent.</p>
 */
public interface ModuleSpan extends AutoCloseable {

    /** v0.0.14 🍊 Reports progress ("retry 2/3: fixing JSON", "calling web_browse"). */
    void state(String text);

    /** v0.0.14 🍊 Attaches a detail to the span (tokens, counts, ids). */
    void detail(String key, Object value);

    /** v0.0.14 🍊 Ends successfully with a short result text. */
    void end(String text);

    /** v0.0.14 🍊 Ends with an error. */
    void fail(Throwable error);

    /** v0.0.14 🍊 Ends because the work was interrupted. */
    void cancelled(String reason);

    /** v0.0.14 🍊 Id of the span (children reference it). */
    String spanId();

    /** v0.0.14 🍊 Ends with "done" unless already ended. */
    @Override
    void close();
}
