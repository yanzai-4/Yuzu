package ai.yuzu.persistence;

import ai.yuzu.common.concurrent.AsyncRunner;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;

/** v0.0.2 🍊 Creates {@link BatchWriter}s sharing the JDBC template, timer and async runner; flushes them on shutdown. */
@Component
public class BatchWriterFactory {

    private static final int DEFAULT_CAPACITY = 20_000;
    private static final int DEFAULT_BATCH = 200;
    private static final Duration DEFAULT_INTERVAL = Duration.ofMillis(200);

    private final JdbcTemplate jdbc;
    private final ScheduledExecutorService timer;
    private final AsyncRunner runner;
    private final List<BatchWriter<?>> writers = new CopyOnWriteArrayList<>();

    /** v0.0.2 🍊 Injects the shared infrastructure. */
    public BatchWriterFactory(JdbcTemplate jdbc, ScheduledExecutorService timerExecutor, AsyncRunner runner) {
        this.jdbc = jdbc;
        this.timer = timerExecutor;
        this.runner = runner;
    }

    /** v0.0.2 🍊 Creates a writer with the default capacity (20k), batch size (200) and interval (200 ms). */
    public <T> BatchWriter<T> create(String name, String sql, ParameterizedPreparedStatementSetter<T> setter) {
        BatchWriter<T> writer = new BatchWriter<>(name, jdbc, sql, setter, DEFAULT_CAPACITY, DEFAULT_BATCH,
                DEFAULT_INTERVAL, timer, runner);
        writers.add(writer);
        return writer;
    }

    /** v0.0.2 🍊 Flushes every writer when the application stops so no telemetry is lost. */
    @PreDestroy
    public void closeAll() {
        writers.forEach(BatchWriter::close);
    }
}
