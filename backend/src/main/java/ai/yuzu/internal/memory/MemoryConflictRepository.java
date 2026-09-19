package ai.yuzu.internal.memory;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.persistence.AgentScopedRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * v0.0.28 🍊 Agent-scoped storage of memory conflicts ({@code memory_conflict}) and their round countdown.
 *
 * <p>Both copies are stored as plain JSON objects (no time fields), so a conflict can be replayed verbatim
 * when the subconscious finally resolves it.</p>
 */
@Repository
public class MemoryConflictRepository extends AgentScopedRepository {

    static final String SQL_INSERT = """
            INSERT INTO memory_conflict (agent_id, id, kind, target_id, old_copy, new_copy, reason, rounds_left,
                status, created_at, updated_at)
            VALUES (:agentId, :id, :kind, :targetId, :oldCopy, :newCopy, :reason, :rounds, 'OPEN', :now, :now)
            """;
    static final String SQL_OPEN = """
            SELECT id, kind, target_id, old_copy, new_copy, reason, rounds_left, status, created_at
            FROM memory_conflict WHERE agent_id = :agentId AND status = 'OPEN' ORDER BY seq
            """;
    static final String SQL_BY_ID = """
            SELECT id, kind, target_id, old_copy, new_copy, reason, rounds_left, status, created_at
            FROM memory_conflict WHERE agent_id = :agentId AND id = :id
            """;
    static final String SQL_RESOLVE = """
            UPDATE memory_conflict SET status = 'RESOLVED', resolution = :resolution, updated_at = :now
            WHERE agent_id = :agentId AND id = :id AND status = 'OPEN'
            """;
    static final String SQL_COUNT_DOWN = """
            UPDATE memory_conflict SET rounds_left = rounds_left - 1, updated_at = :now
            WHERE agent_id = :agentId AND status = 'OPEN' AND rounds_left > 0
            """;
    static final String SQL_EXPIRE = """
            UPDATE memory_conflict SET status = 'EXPIRED',
                resolution = 'No new evidence arrived in time; I kept what I already remembered.', updated_at = :now
            WHERE agent_id = :agentId AND status = 'OPEN' AND rounds_left <= 0
            """;

    private final Jsons jsons;

    /** v0.0.28 🍊 Injects the JDBC client and the JSON helper. */
    public MemoryConflictRepository(JdbcClient jdbc, Jsons jsons) {
        super(jdbc);
        this.jsons = jsons;
    }

    /** v0.0.28 🍊 Opens a conflict that holds for the given number of subconscious rounds; returns its id. */
    public String open(AgentId agentId, MemoryKind kind, StoredMemory oldCopy, MemoryCandidate newCopy,
                       String reason, int rounds, Instant now) {
        return insertWithFreshId(DataName.CONFLICT, agentId, id -> scoped(SQL_INSERT, agentId).param("id", id)
                .param("kind", kind.name()).param("targetId", oldCopy.id())
                .param("oldCopy", jsons.write(toMap(oldCopy))).param("newCopy", jsons.write(toMap(newCopy)))
                .param("reason", reason).param("rounds", rounds).param("now", DbTime.toDb(now)).update());
    }

    /** v0.0.28 🍊 Conflicts still waiting for evidence, oldest first. */
    public List<MemoryConflict> open(AgentId agentId) {
        return scoped(SQL_OPEN, agentId).query(mapper()).list();
    }

    /** v0.0.28 🍊 One conflict by id, whatever its status. */
    public Optional<MemoryConflict> byId(AgentId agentId, String id) {
        return scoped(SQL_BY_ID, agentId).param("id", id).query(mapper()).optional();
    }

    /** v0.0.28 🍊 Closes a conflict with the note of how it was settled. */
    public void resolve(AgentId agentId, String id, String resolution, Instant now) {
        scoped(SQL_RESOLVE, agentId).param("id", id).param("resolution", resolution)
                .param("now", DbTime.toDb(now)).update();
    }

    /** v0.0.28 🍊 Counts one round down on every open conflict; returns how many were touched. */
    public int countDown(AgentId agentId, Instant now) {
        return scoped(SQL_COUNT_DOWN, agentId).param("now", DbTime.toDb(now)).update();
    }

    /** v0.0.28 🍊 Expires every open conflict whose countdown reached zero (the old memory is kept). */
    public int expireElapsed(AgentId agentId, Instant now) {
        return scoped(SQL_EXPIRE, agentId).param("now", DbTime.toDb(now)).update();
    }

    /** v0.0.28 🍊 Row → conflict (both copies come back from their JSON objects). */
    private RowMapper<MemoryConflict> mapper() {
        return (rs, i) -> {
            MemoryKind kind = MemoryKind.valueOf(rs.getString("kind"));
            Instant created = DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class));
            Map<String, String> oldCopy = readMap(rs.getString("old_copy"));
            Map<String, String> newCopy = readMap(rs.getString("new_copy"));
            StoredMemory stored = new StoredMemory(rs.getString("target_id"), kind, text(oldCopy, "title"),
                    text(oldCopy, "scenario"), text(oldCopy, "body"), created);
            MemoryCandidate candidate = new MemoryCandidate(kind, text(newCopy, "title"), text(newCopy, "scenario"),
                    text(newCopy, "body"), keywords(newCopy), text(newCopy, "source"));
            return new MemoryConflict(rs.getString("id"), kind, rs.getString("target_id"), stored, candidate,
                    rs.getString("reason"), rs.getInt("rounds_left"), rs.getString("status"), created);
        };
    }

    /** v0.0.28 🍊 Stored entry → JSON object (no time fields). */
    private static Map<String, String> toMap(StoredMemory memory) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("title", memory.title());
        map.put("scenario", memory.scenario());
        map.put("body", memory.body());
        return map;
    }

    /** v0.0.28 🍊 Candidate → JSON object. */
    private static Map<String, String> toMap(MemoryCandidate candidate) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("title", candidate.title());
        map.put("scenario", candidate.scenario());
        map.put("body", candidate.body());
        map.put("keywords", candidate.keywordLine());
        map.put("source", candidate.source());
        return map;
    }

    /** v0.0.28 🍊 JSON object → string map. */
    private Map<String, String> readMap(String json) {
        return json == null || json.isBlank() ? Map.of() : jsons.read(json, new TypeReference<Map<String, String>>() {
        });
    }

    /** v0.0.28 🍊 A field of a stored copy ("" when absent). */
    private static String text(Map<String, String> map, String key) {
        String value = map.get(key);
        return value == null ? "" : value;
    }

    /** v0.0.28 🍊 The keyword line back as a list. */
    private static List<String> keywords(Map<String, String> map) {
        String line = text(map, "keywords");
        if (line.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : line.split(",")) {
            if (!part.isBlank()) {
                out.add(part.strip());
            }
        }
        return List.copyOf(out);
    }
}
