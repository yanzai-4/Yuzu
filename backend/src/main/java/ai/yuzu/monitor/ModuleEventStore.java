package ai.yuzu.monitor;

import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.persistence.BatchWriter;
import ai.yuzu.persistence.BatchWriterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/** v0.0.12 🍊 Asynchronous, batched persistence of module events into module_event (never blocks the reporting agent). */
@Component
public class ModuleEventStore {

    private static final Logger log = LoggerFactory.getLogger(ModuleEventStore.class);

    // IGNORE: a (very rare) duplicate random record id must cost one row, not the whole multi-row batch.
    static final String INSERT_EVENT = """
            INSERT IGNORE INTO module_event (agent_id, id, module, phase, text, detail, trace_id, span_id,
                parent_span_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final Jsons jsons;
    private final BatchWriter<ModuleEvent> writer;

    /** v0.0.12 🍊 Creates the batch writer (flushes every 200 ms or per 200 rows). */
    public ModuleEventStore(BatchWriterFactory writers, Jsons jsons) {
        this.jsons = jsons;
        this.writer = writers.create("module-events", INSERT_EVENT, this::bind);
    }

    /** v0.0.12 🍊 Queues one event without blocking; false when the queue was full and the row was dropped. */
    public boolean offer(ModuleEvent event) {
        return writer.offer(event);
    }

    /** v0.0.12 🍊 Synchronously writes everything queued (trace reads and tests call this for read-your-writes). */
    public void flush() {
        writer.flush();
    }

    /** v0.0.12 🍊 Rows written so far. */
    public long written() {
        return writer.written();
    }

    /** v0.0.12 🍊 Rows dropped because the queue was full or a batch failed. */
    public long dropped() {
        return writer.dropped();
    }

    /** v0.0.12 🍊 Binds one event to the positional INSERT. */
    private void bind(PreparedStatement ps, ModuleEvent event) throws SQLException {
        ps.setString(1, event.agentId().value());
        ps.setString(2, event.id());
        ps.setString(3, event.module().name());
        ps.setString(4, event.phase().name());
        ps.setString(5, event.text());
        ps.setString(6, detailJson(event));
        ps.setString(7, event.traceId());
        ps.setString(8, event.spanId());
        ps.setString(9, event.parentSpanId());
        ps.setObject(10, DbTime.toDb(event.createdAt()));
    }

    /** v0.0.12 🍊 Detail as JSON text (null when absent or, defensively, when it cannot be serialized). */
    private String detailJson(ModuleEvent event) {
        if (event.detail() == null) {
            return null;
        }
        try {
            return jsons.write(event.detail());
        } catch (RuntimeException e) {
            log.warn("Dropped the detail of module event {}: {}", event.id(), e.getMessage());
            return null;
        }
    }
}
