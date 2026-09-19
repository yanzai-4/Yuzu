package ai.yuzu.agent;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.DbTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** v0.0.6 🍊 Access to the {@code agent} registry table (one row per agent, keyed by agent_id). */
@Repository
public class AgentRepository {

    private static final String COLUMNS = """
            agent_id, room_id, citrus_name, avatar_key, color, role, title, scope_text, persona, permissions, limits,
            state, version, created_at, updated_at""";

    private final JdbcClient jdbc;
    private final Jsons jsons;
    private final RowMapper<AgentProfile> mapper;

    /** v0.0.6 🍊 Injects the JDBC client and JSON helper. */
    public AgentRepository(JdbcClient jdbc, Jsons jsons) {
        this.jdbc = jdbc;
        this.jsons = jsons;
        this.mapper = (rs, i) -> new AgentProfile(
                AgentId.of(rs.getString("agent_id")), rs.getString("room_id"), rs.getString("citrus_name"),
                rs.getString("avatar_key"), rs.getString("color"), Role.valueOf(rs.getString("role")),
                rs.getString("title"), rs.getString("scope_text"), rs.getString("persona"),
                new PermissionScope(jsons.readStringList(rs.getString("permissions")).stream()
                        .map(Permission::valueOf).toList(), jsons.read(rs.getString("limits"), Limits.class)),
                AgentProfile.State.valueOf(rs.getString("state")), rs.getInt("version"),
                DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)),
                DbTime.fromDb(rs.getObject("updated_at", LocalDateTime.class)));
    }

    /** v0.0.6 🍊 Inserts a new agent. */
    public void insert(AgentProfile p) {
        jdbc.sql("""
                        INSERT INTO agent (agent_id, room_id, citrus_name, avatar_key, color, role, title, scope_text,
                            persona, permissions, limits, state, version, created_at, updated_at)
                        VALUES (:id, :room, :name, :avatar, :color, :role, :title, :scope, :persona, :perms, :limits,
                            :state, 0, :created, :updated)
                        """)
                .param("id", p.agentId().value()).param("room", p.roomId()).param("name", p.name())
                .param("avatar", p.avatarKey()).param("color", p.color()).param("role", p.role().name())
                .param("title", p.title()).param("scope", p.scopeText()).param("persona", p.persona())
                .param("perms", jsons.write(p.scope().names())).param("limits", jsons.write(p.scope().limits()))
                .param("state", p.state().name()).param("created", DbTime.toDb(p.createdAt()))
                .param("updated", DbTime.toDb(p.updatedAt()))
                .update();
    }

    /** v0.0.6 🍊 Optimistic update of the editable fields; returns false when the version changed meanwhile. */
    public boolean update(AgentProfile p, int expectedVersion) {
        return jdbc.sql("""
                        UPDATE agent SET title = :title, scope_text = :scope, persona = :persona, permissions = :perms,
                            limits = :limits, state = :state, version = version + 1, updated_at = :updated
                        WHERE agent_id = :id AND version = :version
                        """)
                .param("title", p.title()).param("scope", p.scopeText()).param("persona", p.persona())
                .param("perms", jsons.write(p.scope().names())).param("limits", jsons.write(p.scope().limits()))
                .param("state", p.state().name()).param("updated", DbTime.toDb(p.updatedAt()))
                .param("id", p.agentId().value()).param("version", expectedVersion)
                .update() == 1;
    }

    /** v0.0.6 🍊 Marks an agent retired and frees its citrus name for future hires. */
    public void retire(AgentId agentId, Instant now) {
        jdbc.sql("""
                        UPDATE agent SET state = 'RETIRED', citrus_name = CONCAT(citrus_name, ' #', agent_id),
                            version = version + 1, updated_at = :updated
                        WHERE agent_id = :id AND state <> 'RETIRED'
                        """)
                .param("updated", DbTime.toDb(now)).param("id", agentId.value()).update();
    }

    /** v0.0.6 🍊 Finds an agent by id (any state). */
    public Optional<AgentProfile> findById(AgentId agentId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM agent WHERE agent_id = :id")
                .param("id", agentId.value()).query(mapper).optional();
    }

    /** v0.0.6 🍊 Non-retired agents of a room in creation order. */
    public List<AgentProfile> findPresentByRoom(String roomId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM agent WHERE room_id = :room AND state <> 'RETIRED'"
                        + " ORDER BY created_at, agent_id")
                .param("room", roomId).query(mapper).list();
    }

    /** v0.0.6 🍊 Every non-retired agent (used to start runtimes at boot). */
    public List<AgentProfile> findAllPresent() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM agent WHERE state <> 'RETIRED' ORDER BY created_at")
                .query(mapper).list();
    }

    /** v0.0.6 🍊 Every citrus name used in a room (retired agents carry a '#agent-xxxx' suffix, freeing the name). */
    public List<String> namesInRoom(String roomId) {
        return jdbc.sql("SELECT citrus_name FROM agent WHERE room_id = :room").param("room", roomId)
                .query(String.class).list();
    }

    /** v0.0.6 🍊 True when the agent id is already used. */
    public boolean idExists(AgentId agentId) {
        return jdbc.sql("SELECT COUNT(*) FROM agent WHERE agent_id = :id").param("id", agentId.value())
                .query(Integer.class).single() > 0;
    }
}
