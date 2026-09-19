package ai.yuzu.persistence;

import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * v0.0.31 🍊 A failing telemetry write must never reach an agent: it is counted, logged and forgotten.
 *
 * <p>Telemetry ({@code llm_call}, {@code module_event}) goes through {@link BatchWriter}. A broken statement,
 * a duplicate key or a database that is simply unavailable must not throw on the offering thread, must not
 * stall the writer, and must not stop the rows that come after it.</p>
 */
@IntegrationTest
class BatchWriterFailureTest {

    @Autowired
    private JdbcTemplate jdbc;

    /** v0.0.31 🍊 A write that fails is counted as dropped; offering never throws and later rows still land. */
    @Test
    void failedWritesAreCountedAndTheWriterKeepsWorking() {
        AgentId agent = IdGen.newAgentId();
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            AtomicInteger flushes = new AtomicInteger();
            JdbcTemplate flaky = new JdbcTemplate(jdbc.getDataSource()) {
                @Override
                public <T> int[][] batchUpdate(String sql, java.util.Collection<T> batchArgs, int batchSize,
                                               org.springframework.jdbc.core.ParameterizedPreparedStatementSetter<T> pss) {
                    if (flushes.incrementAndGet() == 1) {
                        throw new org.springframework.dao.DataAccessResourceFailureException("connection lost");
                    }
                    return super.batchUpdate(sql, batchArgs, batchSize, pss);
                }
            };
            try (BatchWriter<String> writer = writer(flaky, agent, timer, executor)) {
                assertThatCode(() -> {
                    writer.offer(IdGen.recordId(DataName.EVENT, agent));
                    writer.offer(IdGen.recordId(DataName.EVENT, agent));
                    writer.flush();
                }).doesNotThrowAnyException();
                assertThat(writer.dropped()).isEqualTo(2);
                assertThat(writer.written()).isZero();

                writer.offer(IdGen.recordId(DataName.EVENT, agent));
                writer.flush();
                assertThat(writer.written()).isEqualTo(1);
            }
            Long persisted = jdbc.queryForObject("SELECT COUNT(*) FROM module_event WHERE agent_id = ?", Long.class,
                    agent.value());
            assertThat(persisted).isEqualTo(1L);
        } finally {
            timer.shutdownNow();
        }
    }

    /** v0.0.31 🍊 A real constraint violation (duplicate id) is contained the same way as a lost connection. */
    @Test
    void constraintViolationsAreContained() {
        AgentId agent = IdGen.newAgentId();
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             BatchWriter<String> writer = writer(jdbc, agent, timer, executor)) {
            String duplicate = IdGen.recordId(DataName.EVENT, agent);
            writer.offer(duplicate);
            writer.flush();
            assertThat(writer.written()).isEqualTo(1);

            writer.offer(duplicate);
            assertThatCode(writer::flush).doesNotThrowAnyException();
            assertThat(writer.dropped()).isEqualTo(1);

            writer.offer(IdGen.recordId(DataName.EVENT, agent));
            writer.flush();
            assertThat(writer.written()).isEqualTo(2);
        } finally {
            timer.shutdownNow();
        }
    }

    /** v0.0.31 🍊 Offering from many threads while writes fail never blocks and never loses the counters. */
    @Test
    void offeringNeverBlocksWhileWritesFail() throws Exception {
        AgentId agent = IdGen.newAgentId();
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        JdbcTemplate broken = new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public <T> int[][] batchUpdate(String sql, java.util.Collection<T> batchArgs, int batchSize,
                                           org.springframework.jdbc.core.ParameterizedPreparedStatementSetter<T> pss) {
                throw new org.springframework.dao.DataAccessResourceFailureException("database is down");
            }
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             BatchWriter<String> writer = writer(broken, agent, timer, executor)) {
            int threads = 8;
            int perThread = 200;
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> writers = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                writers.add(Thread.ofVirtual().start(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int i = 0; i < perThread; i++) {
                        writer.offer(IdGen.recordId(DataName.EVENT, agent));
                    }
                }));
            }
            long startedNanos = System.nanoTime();
            start.countDown();
            for (Thread t : writers) {
                assertThat(t.join(Duration.ofSeconds(30))).as("an offering thread blocked").isTrue();
            }
            long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000;
            assertThat(elapsedMillis).as("offering must stay cheap even when every write fails").isLessThan(30_000);
            writer.flush();
            assertThat(writer.written()).isZero();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (writer.dropped() < (long) threads * perThread && System.nanoTime() < deadline) {
                writer.flush();
                Thread.sleep(5);
            }
            assertThat(writer.dropped()).as("every row is accounted for, none silently vanishes")
                    .isEqualTo((long) threads * perThread);
        } finally {
            timer.shutdownNow();
            assertThat(timer.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** v0.0.31 🍊 A module_event writer with a small queue, so failures and overflow are both exercised. */
    private BatchWriter<String> writer(JdbcTemplate template, AgentId agent, ScheduledExecutorService timer,
                                       java.util.concurrent.ExecutorService executor) {
        return new BatchWriter<>("failure", template, """
                INSERT INTO module_event (agent_id, id, module, phase, text, created_at)
                VALUES (?, ?, 'TEST', 'INFO', 'failure path', UTC_TIMESTAMP(3))
                """, (ps, id) -> {
            ps.setString(1, agent.value());
            ps.setString(2, id);
        }, 64, 500, Duration.ofHours(1), timer, new AsyncRunner(executor, List.of()));
    }
}
