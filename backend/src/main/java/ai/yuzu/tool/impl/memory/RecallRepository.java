package ai.yuzu.tool.impl.memory;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.persistence.AgentScopedRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * v0.0.19 🍊 Read-only recall queries: deep memories (FULLTEXT ngram and/or time), the working-memory archive
 * (compacted entries included) and the agent's own room chat.
 */
@Repository
public class RecallRepository extends AgentScopedRepository {

    /** v0.0.19 🍊 One recalled item. */
    public record Hit(String kind, String title, String text, Instant at) {
    }

    static final String SQL_DEEP_TEXT = """
            SELECT title, content, created_at FROM deep_memory
            WHERE agent_id = :agentId AND status = 'ACTIVE'
              AND MATCH(title, content, keywords) AGAINST (:q IN NATURAL LANGUAGE MODE)
            ORDER BY MATCH(title, content, keywords) AGAINST (:q IN NATURAL LANGUAGE MODE) DESC LIMIT :limit
            """;
    static final String SQL_DEEP_TEXT_RANGE = """
            SELECT title, content, created_at FROM deep_memory
            WHERE agent_id = :agentId AND status = 'ACTIVE'
              AND MATCH(title, content, keywords) AGAINST (:q IN NATURAL LANGUAGE MODE)
              AND (created_at BETWEEN :from AND :to
                   OR (about_from IS NOT NULL AND about_from <= :to AND COALESCE(about_to, about_from) >= :from))
            ORDER BY MATCH(title, content, keywords) AGAINST (:q IN NATURAL LANGUAGE MODE) DESC LIMIT :limit
            """;
    static final String SQL_DEEP_RANGE = """
            SELECT title, content, created_at FROM deep_memory
            WHERE agent_id = :agentId AND status = 'ACTIVE'
              AND (created_at BETWEEN :from AND :to
                   OR (about_from IS NOT NULL AND about_from <= :to AND COALESCE(about_to, about_from) >= :from))
            ORDER BY created_at DESC LIMIT :limit
            """;
    static final String SQL_WM_TEXT = """
            SELECT direction, text, created_at FROM working_memory_entry
            WHERE agent_id = :agentId AND MATCH(text) AGAINST (:q IN NATURAL LANGUAGE MODE)
            ORDER BY MATCH(text) AGAINST (:q IN NATURAL LANGUAGE MODE) DESC LIMIT :limit
            """;
    static final String SQL_WM_RANGE = """
            SELECT direction, text, created_at FROM working_memory_entry
            WHERE agent_id = :agentId AND created_at BETWEEN :from AND :to ORDER BY seq LIMIT :limit
            """;
    private static final String CHAT_TEXT = """
            SELECT author_name, content, created_at FROM chat_message
            WHERE room_id = :room AND MATCH(content) AGAINST (:q IN NATURAL LANGUAGE MODE)
            ORDER BY MATCH(content) AGAINST (:q IN NATURAL LANGUAGE MODE) DESC LIMIT :limit
            """;
    private static final String CHAT_RANGE = """
            SELECT author_name, content, created_at FROM chat_message
            WHERE room_id = :room AND created_at BETWEEN :from AND :to ORDER BY seq LIMIT :limit
            """;

    private static final RowMapper<Hit> DEEP = (rs, i) -> new Hit("deep", rs.getString("title"),
            rs.getString("content"), DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)));
    private static final RowMapper<Hit> WM = (rs, i) -> new Hit("mind",
            "IN".equals(rs.getString("direction")) ? "I noticed" : "I thought/decided", rs.getString("text"),
            DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)));
    private static final RowMapper<Hit> CHAT = (rs, i) -> new Hit("chat", rs.getString("author_name"),
            rs.getString("content"), DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)));

    /** v0.0.19 🍊 Injects the JDBC client. */
    public RecallRepository(JdbcClient jdbc) {
        super(jdbc);
    }

    /** v0.0.19 🍊 Deep memories by keywords and/or range (at least one must be given). */
    public List<Hit> deep(AgentId agentId, String query, Instant from, Instant to, int limit) {
        boolean text = query != null && !query.isBlank();
        if (text && from != null) {
            return scoped(SQL_DEEP_TEXT_RANGE, agentId).param("q", query).param("from", DbTime.toDb(from))
                    .param("to", DbTime.toDb(to)).param("limit", limit).query(DEEP).list();
        }
        if (text) {
            return scoped(SQL_DEEP_TEXT, agentId).param("q", query).param("limit", limit).query(DEEP).list();
        }
        return from == null ? List.of() : scoped(SQL_DEEP_RANGE, agentId).param("from", DbTime.toDb(from))
                .param("to", DbTime.toDb(to)).param("limit", limit).query(DEEP).list();
    }

    /** v0.0.19 🍊 Own past thoughts and inputs (compacted ones included) by range, else by keywords. */
    public List<Hit> mind(AgentId agentId, String query, Instant from, Instant to, int limit) {
        if (from != null) {
            return scoped(SQL_WM_RANGE, agentId).param("from", DbTime.toDb(from)).param("to", DbTime.toDb(to))
                    .param("limit", limit).query(WM).list();
        }
        return query == null || query.isBlank() ? List.of()
                : scoped(SQL_WM_TEXT, agentId).param("q", query).param("limit", limit).query(WM).list();
    }

    /** v0.0.19 🍊 The agent's own room chat by range, else by keywords. */
    public List<Hit> chat(String roomId, String query, Instant from, Instant to, int limit) {
        if (from != null) {
            return jdbc.sql(CHAT_RANGE).param("room", roomId).param("from", DbTime.toDb(from))
                    .param("to", DbTime.toDb(to)).param("limit", limit).query(CHAT).list();
        }
        return query == null || query.isBlank() ? List.of()
                : jdbc.sql(CHAT_TEXT).param("room", roomId).param("q", query).param("limit", limit).query(CHAT).list();
    }
}
