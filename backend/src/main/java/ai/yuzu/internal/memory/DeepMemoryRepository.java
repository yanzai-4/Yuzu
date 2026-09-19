package ai.yuzu.internal.memory;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.persistence.AgentScopedRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** v0.0.28 🍊 Agent-scoped storage of deep memory ({@code deep_memory}, FULLTEXT ngram over title/content/keywords). */
@Repository
public class DeepMemoryRepository extends AgentScopedRepository {

    static final String SQL_INSERT = """
            INSERT INTO deep_memory (agent_id, id, title, content, keywords, source, status, content_hash,
                created_at, updated_at)
            VALUES (:agentId, :id, :title, :content, :keywords, :source, 'ACTIVE', :hash, :now, :now)
            """;
    static final String SQL_BY_HASH = """
            SELECT id, title, content, created_at FROM deep_memory
            WHERE agent_id = :agentId AND status = 'ACTIVE' AND content_hash = :hash LIMIT 1
            """;
    static final String SQL_BY_ID = """
            SELECT id, title, content, created_at FROM deep_memory
            WHERE agent_id = :agentId AND status = 'ACTIVE' AND id = :id
            """;
    static final String SQL_SIMILAR = """
            SELECT id, title, content, created_at FROM deep_memory
            WHERE agent_id = :agentId AND status = 'ACTIVE'
              AND MATCH(title, content, keywords) AGAINST (:q IN NATURAL LANGUAGE MODE)
            ORDER BY MATCH(title, content, keywords) AGAINST (:q IN NATURAL LANGUAGE MODE) DESC LIMIT :limit
            """;
    static final String SQL_UPDATE_BODY = """
            UPDATE deep_memory SET content = :content, content_hash = :hash, updated_at = :now
            WHERE agent_id = :agentId AND id = :id AND status = 'ACTIVE'
            """;
    static final String SQL_SUPERSEDE = """
            UPDATE deep_memory SET status = 'SUPERSEDED', updated_at = :now
            WHERE agent_id = :agentId AND id = :id AND status = 'ACTIVE'
            """;

    private static final RowMapper<StoredMemory> MAPPER = (rs, i) -> new StoredMemory(rs.getString("id"),
            MemoryKind.DEEP, rs.getString("title"), "", rs.getString("content"),
            DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)));

    /** v0.0.28 🍊 Injects the JDBC client. */
    public DeepMemoryRepository(JdbcClient jdbc) {
        super(jdbc);
    }

    /** v0.0.28 🍊 Inserts a deep memory and returns its id (throws DuplicateKeyException on the content hash). */
    public String insert(AgentId agentId, MemoryCandidate candidate, byte[] hash, Instant now) {
        return insertWithFreshId(DataName.MEMORY, agentId, id -> scoped(SQL_INSERT, agentId).param("id", id)
                .param("title", candidate.title()).param("content", candidate.body())
                .param("keywords", candidate.keywordLine()).param("source", candidate.source())
                .param("hash", hash).param("now", DbTime.toDb(now)).update());
    }

    /** v0.0.28 🍊 Active memory with this exact content hash. */
    public Optional<StoredMemory> byHash(AgentId agentId, byte[] hash) {
        return scoped(SQL_BY_HASH, agentId).param("hash", hash).query(MAPPER).optional();
    }

    /** v0.0.28 🍊 Active memory by id. */
    public Optional<StoredMemory> byId(AgentId agentId, String id) {
        return scoped(SQL_BY_ID, agentId).param("id", id).query(MAPPER).optional();
    }

    /** v0.0.28 🍊 Memories the FULLTEXT index considers similar, best first. */
    public List<StoredMemory> similar(AgentId agentId, String query, int limit) {
        return scoped(SQL_SIMILAR, agentId).param("q", query).param("limit", limit).query(MAPPER).list();
    }

    /** v0.0.28 🍊 Replaces the content of a memory (merge). */
    public void updateContent(AgentId agentId, String id, String content, byte[] hash, Instant now) {
        scoped(SQL_UPDATE_BODY, agentId).param("id", id).param("content", content).param("hash", hash)
                .param("now", DbTime.toDb(now)).update();
    }

    /** v0.0.28 🍊 Marks a memory as superseded (overwrite, conflict resolved in favour of the new entry). */
    public void supersede(AgentId agentId, String id, Instant now) {
        scoped(SQL_SUPERSEDE, agentId).param("id", id).param("now", DbTime.toDb(now)).update();
    }
}
