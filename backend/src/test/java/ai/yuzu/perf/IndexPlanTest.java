package ai.yuzu.perf;

import ai.yuzu.support.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * v0.0.31 🍊 EXPLAIN guard: the hot read paths must never degrade to a full table scan.
 *
 * <p>Seeds enough rows (8 agents × 400) that the optimizer cannot prefer a scan out of laziness, runs
 * {@code ANALYZE TABLE}, then EXPLAINs every hot query and fails when {@code type = ALL} or no index is
 * chosen. The SQL is read reflectively from the production repositories, so a rewrite that loses its index
 * fails here instead of in the demo. Ordered clustered-key reads must also avoid a filesort: that is the
 * whole point of {@code PRIMARY KEY (agent_id, seq)}.</p>
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndexPlanTest {

    private static final Logger log = LoggerFactory.getLogger(IndexPlanTest.class);

    /** v0.0.31 🍊 Agents used for seeding (the platform maximum). */
    private static final int AGENTS = 8;
    /** v0.0.31 🍊 Rows per agent per table. */
    private static final int ROWS = 400;
    /** v0.0.31 🍊 The agent whose rows the EXPLAINed queries read. */
    private static final String AGENT = "agent-0a01";
    /** v0.0.31 🍊 The room whose chat the EXPLAINed queries read. */
    private static final String ROOM = "room-0a01";

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private JdbcTemplate template;

    /** v0.0.31 🍊 Seeds realistic volumes once, so the optimizer has a reason to pick an index. */
    @BeforeAll
    void seed() {
        List<String> agentIds = new ArrayList<>();
        for (int a = 0; a < AGENTS; a++) {
            agentIds.add(String.format("agent-0a%02x", a + 1));
        }
        jdbc.sql("INSERT IGNORE INTO room (id, name, created_at) VALUES (:id, 'Plan room', UTC_TIMESTAMP(3))")
                .param("id", ROOM).update();
        for (String agentId : agentIds) {
            batch("""
                    INSERT INTO module_event (agent_id, id, module, phase, text, detail, trace_id, span_id,
                        parent_span_id, created_at)
                    VALUES (?, ?, 'MAIN', 'END', ?, NULL, ?, ?, NULL,
                        DATE_SUB(UTC_TIMESTAMP(3), INTERVAL ? SECOND))
                    """, agentId, i -> new Object[]{agentId, id("evt", agentId, i), "Thinking about citrus " + i,
                    id("trc", agentId, i % 50), id("spn", agentId, i), ROWS - i});
            batch("""
                    INSERT INTO pool_message (agent_id, id, origin, attribution, text, trace_id, causal_depth,
                        emitted_by_main, run_id, consumed_at, created_at)
                    VALUES (?, ?, 'EXTERNAL', 'Alice (human) told me', ?, NULL, 0, 0, ?, UTC_TIMESTAMP(3),
                        DATE_SUB(UTC_TIMESTAMP(3), INTERVAL ? SECOND))
                    """, agentId, i -> new Object[]{agentId, id("pool", agentId, i),
                    "Competitor analysis note " + i, id("run", agentId, i), ROWS - i});
            batch("""
                    INSERT INTO working_memory_entry (agent_id, id, run_id, direction, origin, origin_ref, text,
                        tokens, compacted, created_at)
                    VALUES (?, ?, NULL, 'IN', 'chat', ?, ?, 12, ?,
                        DATE_SUB(UTC_TIMESTAMP(3), INTERVAL ? SECOND))
                    """, agentId, i -> new Object[]{agentId, id("wm", agentId, i), id("ref", agentId, i),
                    "I noticed the competitor analysis for launch " + i, i < ROWS - 10 ? 1 : 0, ROWS - i});
            batch("""
                    INSERT INTO deep_memory (agent_id, id, title, content, keywords, source, status, content_hash,
                        about_from, about_to, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'citrus launch analysis', 'SUBCONSCIOUS', 'ACTIVE', UNHEX(SHA2(?, 256)),
                        DATE_SUB(UTC_TIMESTAMP(3), INTERVAL ? SECOND), UTC_TIMESTAMP(3),
                        DATE_SUB(UTC_TIMESTAMP(3), INTERVAL ? SECOND), UTC_TIMESTAMP(3))
                    """, agentId, i -> new Object[]{agentId, id("mem", agentId, i), "Launch note " + i,
                    "Competitor analysis of the citrus market, entry " + i, agentId + i, ROWS - i, ROWS - i});
        }
        for (int r = 0; r < AGENTS; r++) {
            String roomId = String.format("room-0a%02x", r + 1);
            jdbc.sql("INSERT IGNORE INTO room (id, name, created_at) VALUES (:id, 'Plan room', UTC_TIMESTAMP(3))")
                    .param("id", roomId).update();
            batch("""
                    INSERT INTO chat_message (room_id, id, author_kind, author_id, author_name, kind, content,
                        mentions, mention_all, causal_depth, closure, fanout, stream_state, created_at)
                    VALUES (?, ?, 'HUMAN', 'user-0a01', 'Alice', 'TEXT', ?, JSON_ARRAY(), 0, 0, 0, 1, 'NONE',
                        DATE_SUB(UTC_TIMESTAMP(3), INTERVAL ? SECOND))
                    """, roomId, i -> new Object[]{roomId, id("msg", roomId, i),
                    "Competitor analysis update " + i, ROWS - i});
        }
        for (String table : List.of("module_event", "pool_message", "working_memory_entry", "deep_memory",
                "chat_message")) {
            template.execute("ANALYZE TABLE " + table);
        }
    }

    /** v0.0.31 🍊 Removes the seeded volume again: the schema is shared with the other integration tests. */
    @AfterAll
    void unseed() {
        for (String table : List.of("module_event", "pool_message", "working_memory_entry", "deep_memory")) {
            template.update("DELETE FROM " + table + " WHERE agent_id LIKE 'agent-0a%'");
        }
        template.update("DELETE FROM chat_message WHERE room_id LIKE 'room-0a%'");
        template.update("DELETE FROM room WHERE id LIKE 'room-0a%'");
    }

    /** v0.0.31 🍊 Agent-scoped clustered range scans over (agent_id, seq) use the PRIMARY key with no filesort. */
    @Test
    void agentScopedRangeScansUseTheClusteredKey() {
        Plan latest = explain(sql("ai.yuzu.trace.AgentEventRepository", "SQL_LATEST"),
                Map.of("agentId", quote(AGENT), "limit", "50"));
        assertIndexed(latest, "module_event history (latest)");
        assertNoFilesort(latest, "module_event history (latest)");

        Plan before = explain(sql("ai.yuzu.trace.AgentEventRepository", "SQL_BEFORE"),
                Map.of("agentId", quote(AGENT), "beforeSeq", "300", "limit", "50"));
        assertIndexed(before, "module_event history (paging)");
        assertNoFilesort(before, "module_event history (paging)");

        Plan verbatim = explain(sql("ai.yuzu.internal.memory.WorkingMemoryRepository", "SQL_VERBATIM"),
                Map.of("agentId", quote(AGENT)));
        assertIndexed(verbatim, "working memory (verbatim entries)");

        Plan pool = explain("SELECT id, origin, text FROM pool_message WHERE agent_id = " + quote(AGENT)
                + " ORDER BY seq DESC LIMIT 50", Map.of());
        assertIndexed(pool, "pool_message (recent)");
        assertNoFilesort(pool, "pool_message (recent)");
    }

    /** v0.0.31 🍊 The chat window read (room-clustered, newest first) uses PRIMARY KEY (room_id, seq). */
    @Test
    void chatWindowReadsUseTheRoomClusteredKey() {
        Plan recent = explain("SELECT * FROM chat_message WHERE room_id = " + quote(ROOM)
                + " ORDER BY seq DESC LIMIT 30", Map.of());
        assertIndexed(recent, "chat window (recent)");
        assertNoFilesort(recent, "chat window (recent)");

        Plan paging = explain("SELECT * FROM chat_message WHERE room_id = " + quote(ROOM)
                + " AND seq < 300 ORDER BY seq DESC LIMIT 30", Map.of());
        assertIndexed(paging, "chat window (paging)");
        assertNoFilesort(paging, "chat window (paging)");
    }

    /** v0.0.31 🍊 FULLTEXT recall over deep memory, working memory and chat uses the ngram indexes. */
    @Test
    void fulltextRecallUsesTheNgramIndexes() {
        Plan deep = explain(sql("ai.yuzu.tool.impl.memory.RecallRepository", "SQL_DEEP_TEXT"),
                Map.of("agentId", quote(AGENT), "q", quote("analysis"), "limit", "10"));
        assertFulltext(deep, "deep memory recall");

        Plan working = explain(sql("ai.yuzu.tool.impl.memory.RecallRepository", "SQL_WM_TEXT"),
                Map.of("agentId", quote(AGENT), "q", quote("analysis"), "limit", "10"));
        assertFulltext(working, "working memory recall");

        Plan chat = explain("SELECT author_name, content, created_at FROM chat_message WHERE room_id = "
                + quote(ROOM) + " AND MATCH(content) AGAINST (" + quote("analysis")
                + " IN NATURAL LANGUAGE MODE) LIMIT 10", Map.of());
        assertFulltext(chat, "chat recall");
    }

    /** v0.0.31 🍊 Time-range recall stays on the (agent_id, created_at) secondary indexes. */
    @Test
    void timeRangeRecallUsesTheCreatedAtIndexes() {
        Map<String, String> range = new HashMap<>(Map.of("agentId", quote(AGENT), "limit", "10",
                "from", "DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 DAY)", "to", "UTC_TIMESTAMP(3)"));
        assertIndexed(explain(sql("ai.yuzu.tool.impl.memory.RecallRepository", "SQL_WM_RANGE"), range),
                "working memory time range");
        assertIndexed(explain(sql("ai.yuzu.tool.impl.memory.RecallRepository", "SQL_DEEP_RANGE"), range),
                "deep memory time range");
        assertIndexed(explain(sql("ai.yuzu.internal.memory.WorkingMemoryRepository", "SQL_ARCHIVED_RANGE"), range),
                "working memory archive range");
    }

    /** v0.0.31 🍊 Trace reads find a whole trace through k_trace instead of scanning module_event. */
    @Test
    void traceReadsUseTheTraceIndex() {
        assertIndexed(explain(sql("ai.yuzu.trace.TraceEventRepository", "SQL_BY_TRACE"),
                Map.of("traceId", quote(id("trc", AGENT, 7)), "limit", "200")), "trace by id");
    }

    /** v0.0.31 🍊 The guard has teeth: a query MySQL can only answer by scanning is reported as a failure. */
    @Test
    void theGuardCatchesAFullTableScan() {
        Plan scan = explain("SELECT id FROM module_event WHERE text LIKE '%no index can help here%'", Map.of());
        assertThat(scan.rows().getFirst().type()).isEqualToIgnoringCase("ALL");
        assertThatThrownBy(() -> assertIndexed(scan, "deliberate scan")).isInstanceOf(AssertionError.class);
    }

    /** v0.0.31 🍊 Fails when any row of the plan is a full table scan or picks no index. */
    private static void assertIndexed(Plan plan, String label) {
        log.info("🍊 EXPLAIN {} -> {}", label, plan);
        for (Row row : plan.rows()) {
            assertThat(row.type()).as("%s: %s is a full table scan", label, row.table()).isNotEqualToIgnoringCase("ALL");
            assertThat(row.key()).as("%s: %s uses no index", label, row.table()).isNotNull();
        }
    }

    /** v0.0.31 🍊 Fails when the FULLTEXT index is not the chosen access path. */
    private static void assertFulltext(Plan plan, String label) {
        log.info("🍊 EXPLAIN {} -> {}", label, plan);
        assertThat(plan.rows()).isNotEmpty();
        assertThat(plan.rows().getFirst().type()).as("%s does not use the ngram FULLTEXT index", label)
                .isEqualToIgnoringCase("fulltext");
    }

    /** v0.0.31 🍊 Fails when MySQL has to sort rows the clustered key already delivers in order. */
    private static void assertNoFilesort(Plan plan, String label) {
        assertThat(plan.rows()).noneMatch(r -> r.extra() != null && r.extra().contains("Using filesort"));
        log.debug("🍊 {} needs no filesort", label);
    }

    /** v0.0.31 🍊 Runs EXPLAIN on a query whose named parameters were replaced by literals. */
    private Plan explain(String sqlTemplate, Map<String, String> literals) {
        String statement = bind(sqlTemplate, literals);
        List<Row> rows = jdbc.sql("EXPLAIN " + statement).query((rs, i) -> new Row(rs.getString("table"),
                rs.getString("type"), rs.getString("key"), rs.getString("Extra"))).list();
        List<Row> real = rows.stream().filter(r -> r.table() != null && !r.table().startsWith("<")).toList();
        assertThat(real).as("EXPLAIN produced no table row for: %s", statement).isNotEmpty();
        return new Plan(real);
    }

    /** v0.0.31 🍊 Replaces {@code :name} placeholders with SQL literals (longest name first). */
    private static String bind(String sql, Map<String, String> literals) {
        String bound = sql;
        List<String> names = new ArrayList<>(literals.keySet());
        names.sort((a, b) -> b.length() - a.length());
        for (String name : names) {
            bound = bound.replace(":" + name, literals.get(name));
        }
        assertThat(bound).as("unbound parameter in: %s", bound).doesNotContain(":");
        return bound;
    }

    /** v0.0.31 🍊 Reads a package-private SQL constant of a production repository (keeps the test in sync). */
    private static String sql(String className, String field) {
        try {
            Field f = Class.forName(className).getDeclaredField(field);
            f.setAccessible(true);
            return (String) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot read " + className + "." + field
                    + "; the hot query moved and this guard must be updated.", e);
        }
    }

    /** v0.0.31 🍊 Inserts {@link #ROWS} rows in one batch. */
    private void batch(String insert, String owner, java.util.function.IntFunction<Object[]> args) {
        List<Object[]> values = new ArrayList<>(ROWS);
        for (int i = 0; i < ROWS; i++) {
            values.add(args.apply(i));
        }
        template.batchUpdate(insert, values);
        log.debug("🍊 seeded {} rows for {}", values.size(), owner);
    }

    /** v0.0.31 🍊 Deterministic record id for seeded rows. */
    private static String id(String prefix, String owner, int i) {
        return prefix + "-" + owner.substring(owner.length() - 4) + "-" + String.format("%06d", i);
    }

    /** v0.0.31 🍊 Single-quoted SQL literal. */
    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    /** v0.0.31 🍊 The EXPLAIN rows that refer to a real table. */
    private record Plan(List<Row> rows) {
        @Override
        public String toString() {
            return rows.toString();
        }
    }

    /** v0.0.31 🍊 One EXPLAIN row. */
    private record Row(String table, String type, String key, String extra) {
        @Override
        public String toString() {
            return table + "[type=" + type + ", key=" + key + (extra == null ? "" : ", extra=" + extra) + "]";
        }
    }
}
