package ai.yuzu.chat;

import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.DbTime;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** v0.0.5 🍊 Access to the room-scoped {@code chat_message} table (clustered by room_id, seq). */
@Repository
public class ChatMessageRepository {

    private static final String COLUMNS = """
            room_id, seq, id, author_kind, author_id, author_name, kind, content, mentions, mention_all, closure,
            causal_depth, reply_to, card_id, fanout, stream_state, trace_id, created_at""";

    private final JdbcClient jdbc;
    private final Jsons jsons;
    private final RowMapper<ChatMessage> mapper;

    /** v0.0.5 🍊 Injects the JDBC client and JSON helper. */
    public ChatMessageRepository(JdbcClient jdbc, Jsons jsons) {
        this.jdbc = jdbc;
        this.jsons = jsons;
        this.mapper = (rs, i) -> new ChatMessage(
                rs.getString("id"), rs.getString("room_id"), rs.getLong("seq"),
                AuthorKind.valueOf(rs.getString("author_kind")), rs.getString("author_id"),
                rs.getString("author_name"), MessageKind.valueOf(rs.getString("kind")), rs.getString("content"),
                jsons.readStringList(rs.getString("mentions")), rs.getBoolean("mention_all"),
                rs.getBoolean("closure"), rs.getInt("causal_depth"), rs.getString("reply_to"),
                rs.getString("card_id"), rs.getBoolean("fanout"), StreamState.valueOf(rs.getString("stream_state")),
                rs.getString("trace_id"), DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)));
    }

    /** v0.0.5 🍊 Inserts a message and returns its generated room sequence number. */
    public long insert(ChatMessage m) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO chat_message (room_id, id, author_kind, author_id, author_name, kind, content,
                            mentions, mention_all, closure, causal_depth, reply_to, card_id, fanout, stream_state,
                            trace_id, created_at)
                        VALUES (:room, :id, :authorKind, :authorId, :authorName, :kind, :content, :mentions,
                            :mentionAll, :closure, :depth, :replyTo, :cardId, :fanout, :stream, :trace, :created)
                        """)
                .param("room", m.roomId()).param("id", m.id()).param("authorKind", m.authorKind().name())
                .param("authorId", m.authorId()).param("authorName", m.authorName()).param("kind", m.kind().name())
                .param("content", m.content()).param("mentions", jsons.write(m.mentions()))
                .param("mentionAll", m.mentionAll()).param("closure", m.closure()).param("depth", m.causalDepth())
                .param("replyTo", m.replyTo()).param("cardId", m.cardId()).param("fanout", m.fanout())
                .param("stream", m.streamState().name()).param("trace", m.traceId())
                .param("created", DbTime.toDb(m.createdAt()))
                .update(keys, "seq");
        return keys.getKey().longValue();
    }

    /** v0.0.5 🍊 Persists the final content and stream state of a streamed message. */
    public void updateContent(String roomId, String id, String content, StreamState state) {
        jdbc.sql("UPDATE chat_message SET content = :content, stream_state = :state WHERE room_id = :room AND id = :id")
                .param("content", content).param("state", state.name()).param("room", roomId).param("id", id)
                .update();
    }

    /** v0.0.5 🍊 Latest {@code limit} messages of a room in ascending order. */
    public List<ChatMessage> findRecent(String roomId, int limit) {
        List<ChatMessage> desc = jdbc.sql("SELECT " + COLUMNS + " FROM chat_message WHERE room_id = :room"
                        + " ORDER BY seq DESC LIMIT :limit")
                .param("room", roomId).param("limit", limit).query(mapper).list();
        List<ChatMessage> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return asc;
    }

    /** v0.0.5 🍊 Up to {@code limit} messages older than {@code beforeSeq}, ascending (history paging). */
    public List<ChatMessage> findBefore(String roomId, long beforeSeq, int limit) {
        List<ChatMessage> desc = jdbc.sql("SELECT " + COLUMNS + " FROM chat_message WHERE room_id = :room"
                        + " AND seq < :before ORDER BY seq DESC LIMIT :limit")
                .param("room", roomId).param("before", beforeSeq).param("limit", limit).query(mapper).list();
        List<ChatMessage> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return asc;
    }

    /** v0.0.5 🍊 Finds one message by id. */
    public Optional<ChatMessage> findById(String roomId, String id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM chat_message WHERE room_id = :room AND id = :id")
                .param("room", roomId).param("id", id).query(mapper).optional();
    }
}
