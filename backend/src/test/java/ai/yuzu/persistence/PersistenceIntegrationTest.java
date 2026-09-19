package ai.yuzu.persistence;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.2 🍊 Verifies the V1 schema, ngram FULLTEXT search, per-agent isolation and batched writes. */
@IntegrationTest
class PersistenceIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private BatchWriterFactory batchWriters;

    /** v0.0.2 🍊 English words containing 'a'/'i' are searchable (ngram stopword trap is disabled). */
    @Test
    void ngramFullTextFindsEnglishWords() throws Exception {
        AgentId agent = IdGen.newAgentId();
        insertDeepMemory(agent, "Competitor analysis", "The data shows citrus pricing is rising in Asia.");
        insertDeepMemory(agent, "Lunch", "We ordered noodles.");

        List<String> hits = jdbc.sql("""
                        SELECT title FROM deep_memory
                        WHERE agent_id = :agentId AND MATCH(title, content, keywords) AGAINST (:q IN NATURAL LANGUAGE MODE)
                        """)
                .param("agentId", agent.value()).param("q", "analysis").query(String.class).list();
        assertThat(hits).containsExactly("Competitor analysis");

        List<String> dataHits = jdbc.sql("""
                        SELECT title FROM deep_memory
                        WHERE agent_id = :agentId AND MATCH(title, content, keywords) AGAINST (:q IN BOOLEAN MODE)
                        """)
                .param("agentId", agent.value()).param("q", "\"data\"").query(String.class).list();
        assertThat(dataHits).containsExactly("Competitor analysis");
    }

    /** v0.0.2 🍊 An agent-scoped repository never returns another agent's rows. */
    @Test
    void agentScopedRepositoryIsolatesAgents() throws Exception {
        ProbeRepository repo = new ProbeRepository(jdbc);
        AgentId a = IdGen.newAgentId();
        AgentId b = IdGen.newAgentId();
        insertDeepMemory(a, "A secret", "only for A");
        insertDeepMemory(b, "B secret", "only for B");

        assertThat(repo.titles(a)).containsExactly("A secret");
        assertThat(repo.titles(b)).containsExactly("B secret");
        assertThatThrownBy(repo::unscopedQuery).isInstanceOf(IllegalStateException.class);
    }

    /** v0.0.2 🍊 The batch writer persists every offered row. */
    @Test
    void batchWriterFlushesRows() {
        AgentId agent = IdGen.newAgentId();
        BatchWriter<String> writer = batchWriters.create("test-events", """
                        INSERT INTO module_event (agent_id, id, module, phase, text, created_at)
                        VALUES (?, ?, 'TEST', 'INFO', ?, UTC_TIMESTAMP(3))
                        """,
                (ps, text) -> {
                    ps.setString(1, agent.value());
                    ps.setString(2, IdGen.recordId(DataName.EVENT, agent));
                    ps.setString(3, text);
                });
        for (int i = 0; i < 1_000; i++) {
            assertThat(writer.offer("event " + i)).isTrue();
        }
        writer.flush();
        Long count = jdbc.sql("SELECT COUNT(*) FROM module_event WHERE agent_id = :agentId")
                .param("agentId", agent.value()).query(Long.class).single();
        assertThat(count).isEqualTo(1_000L);
        assertThat(writer.dropped()).isZero();
    }

    /** v0.0.2 🍊 The default room is seeded by the migration. */
    @Test
    void defaultRoomIsSeeded() {
        assertThat(jdbc.sql("SELECT name FROM room WHERE id = 'room-0001'").query(String.class).single())
                .isEqualTo("Citrus HQ");
    }

    /** v0.0.2 🍊 Inserts a deep-memory row directly (the memory service is built in a later step). */
    private void insertDeepMemory(AgentId agent, String title, String content) throws Exception {
        Instant now = Instant.now();
        byte[] hash = MessageDigest.getInstance("SHA-256").digest((title + content).getBytes(StandardCharsets.UTF_8));
        jdbc.sql("""
                        INSERT INTO deep_memory (agent_id, id, title, content, keywords, source, content_hash, created_at, updated_at)
                        VALUES (:agentId, :id, :title, :content, '', 'MANUAL', :hash, :now, :now)
                        """)
                .param("agentId", agent.value())
                .param("id", IdGen.recordId(DataName.MEMORY, agent))
                .param("title", title)
                .param("content", content)
                .param("hash", hash)
                .param("now", DbTime.toDb(now))
                .update();
    }

    /** v0.0.2 🍊 Minimal agent-scoped repository used to exercise the base-class guard. */
    static final class ProbeRepository extends AgentScopedRepository {

        static final String SQL_TITLES = "SELECT title FROM deep_memory WHERE agent_id = :agentId ORDER BY seq";

        ProbeRepository(JdbcClient jdbc) {
            super(jdbc);
        }

        List<String> titles(AgentId agent) {
            return scoped(SQL_TITLES, agent).query(String.class).list();
        }

        void unscopedQuery() {
            scoped("SELECT title FROM deep_memory", AgentId.SYSTEM).query(String.class).list();
        }
    }
}
