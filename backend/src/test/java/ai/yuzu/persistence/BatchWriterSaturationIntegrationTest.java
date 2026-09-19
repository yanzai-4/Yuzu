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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.12 🍊 A saturated telemetry writer retains every row for asynchronous persistence instead of creating holes. */
@IntegrationTest
class BatchWriterSaturationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    /** v0.0.12 🍊 A one-row primary queue overflows deterministically, then flush persists every offered row. */
    @Test
    void saturationRetainsEveryOfferedRow() {
        AgentId agent = IdGen.newAgentId();
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             BatchWriter<Row> writer = new BatchWriter<>("saturation", jdbc, """
                     INSERT INTO module_event (agent_id, id, module, phase, text, created_at)
                     VALUES (?, ?, 'TEST', 'INFO', ?, UTC_TIMESTAMP(3))
                     """, (ps, row) -> {
                 ps.setString(1, agent.value());
                 ps.setString(2, row.id());
                 ps.setString(3, row.text());
             }, 1, 10_000, Duration.ofHours(1), timer, new AsyncRunner(executor, List.of()))) {
            for (int i = 1; i <= 5; i++) {
                assertThat(writer.offer(new Row(IdGen.recordId(DataName.EVENT, agent), "saturated " + i))).isTrue();
            }
            writer.flush();

            Long persisted = jdbc.queryForObject("SELECT COUNT(*) FROM module_event WHERE agent_id = ?", Long.class,
                    agent.value());
            assertThat(persisted).isEqualTo(5L);
            assertThat(writer.dropped()).isZero();
        } finally {
            timer.shutdownNow();
        }
    }

    /** v0.0.12 🍊 One row sent through the saturated writer. */
    private record Row(String id, String text) {
    }
}
