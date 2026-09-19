package ai.yuzu.common.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * v0.0.1 🍊 Runs background work on virtual threads and guarantees no exception is silently lost.
 *
 * <p>Every task is wrapped: a thrown {@link Throwable} is logged and forwarded to all registered
 * {@link ErrorSink}s (for example the realtime error reporter), then the future completes exceptionally.</p>
 */
public class AsyncRunner {

    private static final Logger log = LoggerFactory.getLogger(AsyncRunner.class);

    private final ExecutorService executor;
    private final List<ErrorSink> sinks;

    /** v0.0.1 🍊 Creates a runner over a (virtual-thread) executor and the error sinks. */
    public AsyncRunner(ExecutorService executor, List<ErrorSink> sinks) {
        this.executor = executor;
        this.sinks = List.copyOf(sinks);
    }

    /** v0.0.1 🍊 Fire-and-forget execution with guaranteed error reporting. */
    public void run(String context, String agentId, Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                report(context, agentId, t);
            }
        });
    }

    /** v0.0.1 🍊 Asynchronous computation; failures are reported and propagate to the future. */
    public <T> CompletableFuture<T> supply(String context, String agentId, Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                report(context, agentId, t);
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /** v0.0.1 🍊 Forwards a failure to every sink; a failing sink never breaks the others. */
    public void report(String context, String agentId, Throwable error) {
        for (ErrorSink sink : sinks) {
            try {
                sink.report(context, agentId, error);
            } catch (Throwable sinkFailure) {
                log.error("Error sink {} failed", sink.getClass().getSimpleName(), sinkFailure);
            }
        }
    }

    /** v0.0.1 🍊 The underlying executor (for components that manage their own scheduling). */
    public ExecutorService executor() {
        return executor;
    }
}
