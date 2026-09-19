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

/** v0.0.28 🍊 Agent-scoped storage of habit memory ({@code habit_memory}, FULLTEXT ngram over name/scenario/technique). */
@Repository
public class HabitMemoryRepository extends AgentScopedRepository {

    static final String SQL_INSERT = """
            INSERT INTO habit_memory (agent_id, id, name, scenario, technique, status, uses, content_hash,
                created_at, updated_at)
            VALUES (:agentId, :id, :name, :scenario, :technique, 'ACTIVE', 0, :hash, :now, :now)
            """;
    static final String SQL_BY_HASH = """
            SELECT id, name, scenario, technique, created_at FROM habit_memory
            WHERE agent_id = :agentId AND status = 'ACTIVE' AND content_hash = :hash LIMIT 1
            """;
    static final String SQL_BY_ID = """
            SELECT id, name, scenario, technique, created_at FROM habit_memory
            WHERE agent_id = :agentId AND status = 'ACTIVE' AND id = :id
            """;
    static final String SQL_SIMILAR = """
            SELECT id, name, scenario, technique, created_at FROM habit_memory
            WHERE agent_id = :agentId AND status = 'ACTIVE'
              AND MATCH(name, scenario, technique) AGAINST (:q IN NATURAL LANGUAGE MODE)
            ORDER BY MATCH(name, scenario, technique) AGAINST (:q IN NATURAL LANGUAGE MODE) DESC LIMIT :limit
            """;
    static final String SQL_UPDATE_BODY = """
            UPDATE habit_memory SET technique = :technique, content_hash = :hash, updated_at = :now
            WHERE agent_id = :agentId AND id = :id AND status = 'ACTIVE'
            """;
    static final String SQL_SUPERSEDE = """
            UPDATE habit_memory SET status = 'SUPERSEDED', updated_at = :now
            WHERE agent_id = :agentId AND id = :id AND status = 'ACTIVE'
            """;
    static final String SQL_ACTIVE = """
            SELECT id, name, scenario, technique, created_at FROM habit_memory
            WHERE agent_id = :agentId AND status = 'ACTIVE' ORDER BY seq
            """;

    private static final RowMapper<StoredMemory> MAPPER = (rs, i) -> new StoredMemory(rs.getString("id"),
            MemoryKind.HABIT, rs.getString("name"), rs.getString("scenario"), rs.getString("technique"),
            DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)));

    /** v0.0.28 🍊 Injects the JDBC client. */
    public HabitMemoryRepository(JdbcClient jdbc) {
        super(jdbc);
    }

    /** v0.0.28 🍊 Inserts a habit and returns its id (throws DuplicateKeyException on the content hash). */
    public String insert(AgentId agentId, MemoryCandidate candidate, byte[] hash, Instant now) {
        return insertWithFreshId(DataName.HABIT, agentId, id -> scoped(SQL_INSERT, agentId).param("id", id)
                .param("name", candidate.title()).param("scenario", candidate.scenario())
                .param("technique", candidate.body()).param("hash", hash).param("now", DbTime.toDb(now)).update());
    }

    /** v0.0.28 🍊 Active habit with this exact content hash. */
    public Optional<StoredMemory> byHash(AgentId agentId, byte[] hash) {
        return scoped(SQL_BY_HASH, agentId).param("hash", hash).query(MAPPER).optional();
    }

    /** v0.0.28 🍊 Active habit by id. */
    public Optional<StoredMemory> byId(AgentId agentId, String id) {
        return scoped(SQL_BY_ID, agentId).param("id", id).query(MAPPER).optional();
    }

    /** v0.0.28 🍊 Habits the FULLTEXT index considers similar, best first. */
    public List<StoredMemory> similar(AgentId agentId, String query, int limit) {
        return scoped(SQL_SIMILAR, agentId).param("q", query).param("limit", limit).query(MAPPER).list();
    }

    /** v0.0.28 🍊 Replaces the technique of a habit (merge). */
    public void updateTechnique(AgentId agentId, String id, String technique, byte[] hash, Instant now) {
        scoped(SQL_UPDATE_BODY, agentId).param("id", id).param("technique", technique).param("hash", hash)
                .param("now", DbTime.toDb(now)).update();
    }

    /** v0.0.28 🍊 Marks a habit as superseded (overwrite, conflict resolved in favour of the new entry). */
    public void supersede(AgentId agentId, String id, Instant now) {
        scoped(SQL_SUPERSEDE, agentId).param("id", id).param("now", DbTime.toDb(now)).update();
    }

    /** v0.0.28 🍊 Every active habit, oldest first (the cognition index). */
    public List<StoredMemory> active(AgentId agentId) {
        return scoped(SQL_ACTIVE, agentId).query(MAPPER).list();
    }
}
