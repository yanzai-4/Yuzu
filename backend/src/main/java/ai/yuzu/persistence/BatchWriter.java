package ai.yuzu.persistence;

import ai.yuzu.common.concurrent.AsyncRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * v0.0.2 🍊 Asynchronous batched INSERT writer for high-volume telemetry rows (LLM calls, module events).
 *
 * <p>{@link #offer(Object)} never blocks the caller: rows go into a bounded queue that is flushed with
 * {@code JdbcTemplate.batchUpdate} (rewritten into multi-row INSERTs by the driver) every interval or
 * as soon as a full batch is waiting. When the queue is full the row is dropped and counted, because
 * telemetry must never slow an agent down.</p>
 */
public final class BatchWriter<T> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BatchWriter.class);

    private final String name;
    private final JdbcTemplate jdbc;
    private final String sql;
    private final ParameterizedPreparedStatementSetter<T> setter;
    private final BlockingQueue<T> queue;
    private final int batchSize;
    private final AsyncRunner runner;
    private final ReentrantLock flushLock = new ReentrantLock();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private final LongAdder written = new LongAdder();
    private final LongAdder dropped = new LongAdder();
    private final ScheduledFuture<?> ticker;

    /** v0.0.2 🍊 Creates a writer; use {@link BatchWriterFactory} instead of calling this directly. */
    BatchWriter(String name, JdbcTemplate jdbc, String sql, ParameterizedPreparedStatementSetter<T> setter,
                int capacity, int batchSize, Duration interval, ScheduledExecutorService timer, AsyncRunner runner) {
        this.name = name;
        this.jdbc = jdbc;
        this.sql = sql;
        this.setter = setter;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.batchSize = batchSize;
        this.runner = runner;
        long millis = interval.toMillis();
        this.ticker = timer.scheduleWithFixedDelay(this::scheduleFlush, millis, millis, TimeUnit.MILLISECONDS);
    }

    /** v0.0.2 🍊 Enqueues a row without blocking; returns false (and counts a drop) when the queue is full. */
    public boolean offer(T row) {
        if (!queue.offer(row)) {
            dropped.increment();
            return false;
        }
        if (queue.size() >= batchSize) {
            scheduleFlush();
        }
        return true;
    }

    /** v0.0.2 🍊 Synchronously writes everything currently queued (used by tests and on shutdown). */
    public void flush() {
        flushLock.lock();
        try {
            List<T> chunk = new ArrayList<>(batchSize);
            while (queue.drainTo(chunk, batchSize) > 0) {
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
        if (queue.isEmpty() || !flushScheduled.compareAndSet(false, true)) {
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
