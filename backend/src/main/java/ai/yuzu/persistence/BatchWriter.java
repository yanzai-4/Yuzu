package ai.yuzu.persistence;

import ai.yuzu.common.concurrent.AsyncRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * v0.0.2 🍊 Asynchronous batched INSERT writer for high-volume telemetry rows (LLM calls, module events).
 *
 * <p>{@link #offer(Object)} never blocks the caller: rows go into a bounded queue that is flushed with
 * {@code JdbcTemplate.batchUpdate} (rewritten into multi-row INSERTs by the driver) every interval or
 * as soon as a full batch is waiting. A caller is never blocked: when the bounded queue is full the row
 * spills into an overflow buffer (counted and logged as saturation) and a flush is scheduled immediately,
 * so a burst costs memory instead of holes in the telemetry. Only when the overflow ceiling is also
 * reached are rows dropped and counted, because telemetry must never slow an agent down.</p>
 */
public final class BatchWriter<T> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BatchWriter.class);

    /** v0.0.26 🍊 Overflow buffer size as a multiple of the bounded queue's capacity. */
    private static final int OVERFLOW_FACTOR = 16;
    /** v0.0.26 🍊 Smallest overflow buffer, so even a tiny queue survives a burst without holes. */
    private static final int MIN_OVERFLOW = 1_000;

    private final String name;
    private final JdbcTemplate jdbc;
    private final String sql;
    private final ParameterizedPreparedStatementSetter<T> setter;
    private final BlockingQueue<T> queue;
    private final Queue<T> overflow = new ConcurrentLinkedQueue<>();
    private final AtomicInteger overflowSize = new AtomicInteger();
    private final int overflowCapacity;
    private final int batchSize;
    private final AsyncRunner runner;
    private final ReentrantLock flushLock = new ReentrantLock();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private final LongAdder written = new LongAdder();
    private final LongAdder dropped = new LongAdder();
    private final LongAdder saturated = new LongAdder();
    private final ScheduledFuture<?> ticker;

    /** v0.0.2 🍊 Creates a writer; use {@link BatchWriterFactory} instead of calling this directly. */
    BatchWriter(String name, JdbcTemplate jdbc, String sql, ParameterizedPreparedStatementSetter<T> setter,
                int capacity, int batchSize, Duration interval, ScheduledExecutorService timer, AsyncRunner runner) {
        this.name = name;
        this.jdbc = jdbc;
        this.sql = sql;
        this.setter = setter;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.overflowCapacity = Math.max(OVERFLOW_FACTOR * capacity, MIN_OVERFLOW);
        this.batchSize = batchSize;
        this.runner = runner;
        long millis = interval.toMillis();
        this.ticker = timer.scheduleWithFixedDelay(this::scheduleFlush, millis, millis, TimeUnit.MILLISECONDS);
    }

    /** v0.0.26 🍊 Enqueues without blocking; a full queue spills into the overflow buffer instead of losing the row. */
    public boolean offer(T row) {
        if (!queue.offer(row)) {
            return spill(row);
        }
        if (queue.size() >= batchSize) {
            scheduleFlush();
        }
        return true;
    }

    /** v0.0.26 🍊 Buffers a row the bounded queue could not take; only a full overflow buffer drops it. */
    private boolean spill(T row) {
        if (overflowSize.get() >= overflowCapacity) {
            dropped.increment();
            log.warn("Batch writer {} dropped a row: queue and overflow buffer are both full.", name);
            return false;
        }
        overflow.add(row);
        overflowSize.incrementAndGet();
        saturated.increment();
        scheduleFlush();
        return true;
    }

    /** v0.0.2 🍊 Synchronously writes everything currently queued (used by tests and on shutdown). */
    public void flush() {
        flushLock.lock();
        try {
            List<T> chunk = new ArrayList<>(batchSize);
            while (drainInto(chunk) > 0) {
                try {
                    jdbc.batchUpdate(sql, chunk, chunk.size(), setter);
                    written.add(chunk.size());
                } catch (RuntimeException e) {
                    dropped.add(chunk.size());
                    log.warn("Batch writer {} failed to write {} rows: {}", name, chunk.size(), e.getMessage());
                }
                chunk.clear();
            }
        } finally {
            flushLock.unlock();
        }
    }

    /** v0.0.26 🍊 Fills one chunk, oldest first: the bounded queue first, then the overflow buffer. */
    private int drainInto(List<T> chunk) {
        int taken = queue.drainTo(chunk, batchSize);
        while (taken < batchSize) {
            T row = overflow.poll();
            if (row == null) {
                break;
            }
            overflowSize.decrementAndGet();
            chunk.add(row);
            taken++;
        }
        return taken;
    }

    /** v0.0.26 🍊 Number of rows that had to use the overflow buffer (queue saturation, visible to monitoring). */
    public long saturated() {
        return saturated.sum();
    }

    /** v0.0.2 🍊 Number of rows written so far. */
    public long written() {
        return written.sum();
    }

    /** v0.0.2 🍊 Number of rows dropped because the queue was full or the write failed. */
    public long dropped() {
        return dropped.sum();
    }

    /** v0.0.2 🍊 Stops the ticker and flushes the remaining rows. */
    @Override
    public void close() {
        ticker.cancel(false);
        flush();
    }

    /** v0.0.2 🍊 Hands one flush to a virtual thread (timers must never block on I/O). */
    private void scheduleFlush() {
        if ((queue.isEmpty() && overflow.isEmpty()) || !flushScheduled.compareAndSet(false, true)) {
            return;
        }
        runner.run("batch-writer:" + name, null, () -> {
            try {
                flush();
            } finally {
                flushScheduled.set(false);
            }
        });
    }
}
