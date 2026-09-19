package ai.yuzu.card;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.chat.ChatPost;
import ai.yuzu.chat.ChatService;
import ai.yuzu.chat.MessageKind;
import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.ConflictException;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.DbTime;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.persistence.AgentScopedRepository;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.room.UserView;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * v0.0.19 🍊 Question and approval cards: an agent asks, a human answers through REST (never through chat).
 *
 * <p>A card is posted as a QUESTION_CARD / APPROVAL_CARD chat message with {@code fanout=false}, so no chat
 * module ever evaluates it or its answer (including free-text "Other" answers). The first valid answer wins
 * (optimistic update); the handler registered for the card's purpose receives it asynchronously.</p>
 */
@Service
public class CardService extends AgentScopedRepository {

    /** v0.0.19 🍊 Purpose of plain questions (answer goes to the asking agent's mind). */
    public static final String QUESTION = "QUESTION";

    static final String SQL_INSERT = """
            INSERT INTO question_card (agent_id, id, room_id, kind, purpose, message_id, batch_id, tool_call_id,
                prompt, options, allow_other, payload, status, created_at)
            VALUES (:agentId, :id, :room, :kind, :purpose, :message, :batch, :toolCall, :prompt, :options, :allowOther,
                :payload, 'OPEN', :created)
            """;
    static final String SQL_SET_MESSAGE = """
            UPDATE question_card SET message_id = :message WHERE agent_id = :agentId AND id = :id
            """;
    static final String SQL_ANSWER = """
            UPDATE question_card SET status = 'ANSWERED', answer = :answer, answered_by = :by,
                answered_by_name = :byName, answered_at = :at
            WHERE agent_id = :agentId AND id = :id AND status = 'OPEN'
            """;
    static final String SQL_FIND = """
            SELECT agent_id, id, room_id, kind, purpose, message_id, prompt, options, allow_other, payload, status,
                answer, answered_by_name, created_at, answered_at
            FROM question_card WHERE agent_id = :agentId AND id = :id
            """;

    /** v0.0.19 🍊 A stored card row. */
    private record Row(String agentId, String id, String roomId, String kind, String purpose, String messageId,
                       String prompt, List<CardView.Option> options, boolean allowOther, String payload, String status,
                       CardView.Answer answer, String answeredByName, Instant created, Instant answered) {
    }

    private final Jsons jsons;
    private final NaturalTime time;
    private final SseHub hub;
    private final ChatService chat;
    private final HumanUserService users;
    private final AsyncRunner runner;
    private final ObjectProvider<CardAnswerHandler> handlers;
    private final RowMapper<Row> mapper;

    /** v0.0.19 🍊 Injects collaborators. */
    public CardService(JdbcClient jdbc, Jsons jsons, NaturalTime time, SseHub hub, ChatService chat,
                       HumanUserService users, AsyncRunner runner, ObjectProvider<CardAnswerHandler> handlers) {
        super(jdbc);
        this.jsons = jsons;
        this.time = time;
        this.hub = hub;
        this.chat = chat;
        this.users = users;
        this.runner = runner;
        this.handlers = handlers;
        this.mapper = (rs, i) -> new Row(rs.getString("agent_id"), rs.getString("id"), rs.getString("room_id"),
                rs.getString("kind"), rs.getString("purpose"), rs.getString("message_id"), rs.getString("prompt"),
                jsons.read(rs.getString("options"), new TypeReference<List<CardView.Option>>() {
                }), rs.getBoolean("allow_other"), rs.getString("payload"), rs.getString("status"),
                rs.getString("answer") == null ? null : jsons.read(rs.getString("answer"), CardView.Answer.class),
                rs.getString("answered_by_name"), DbTime.fromDb(rs.getObject("created_at", LocalDateTime.class)),
                DbTime.fromDb(rs.getObject("answered_at", LocalDateTime.class)));
    }

    /**
     * v0.0.19 🍊 Opens a card and posts it in the chat (never fanned out to chat modules).
     *
     * @param approval true for an approval card (options Approve / Reject)
     * @param purpose  which {@link CardAnswerHandler} receives the answer
     * @param payload  JSON stored with the card for the handler (may be null)
     */
    public CardView open(AgentContext ctx, boolean approval, String prompt, List<String> options, boolean allowOther,
                         String purpose, String payload, String batchId, String toolCallId) {
        if (prompt == null || prompt.isBlank()) {
            throw new BadRequestException("A card needs a question.");
        }
        List<CardView.Option> opts = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            opts.add(new CardView.Option("o" + (i + 1), options.get(i).strip()));
        }
        Instant now = time.nowInstant();
        String kind = approval ? "APPROVAL" : "QUESTION";
        String id = insertWithFreshId(DataName.CARD, ctx.agentId(), fresh -> scoped(SQL_INSERT, ctx.agentId())
                .param("id", fresh).param("room", ctx.roomId()).param("kind", kind).param("purpose", purpose)
                .param("message", null).param("batch", batchId).param("toolCall", toolCallId)
                .param("prompt", prompt.strip()).param("options", jsons.write(opts)).param("allowOther", allowOther)
                .param("payload", payload).param("created", DbTime.toDb(now)).update());
        ChatMessage message = chat.post(ChatPost.agent(ctx.roomId(), ctx.agentId().value(), ctx.profile().name(),
                        prompt.strip(), 0, true, ctx.traceId())
                .withKind(approval ? MessageKind.APPROVAL_CARD : MessageKind.QUESTION_CARD).withCard(id));
        scoped(SQL_SET_MESSAGE, ctx.agentId()).param("id", id).param("message", message.id()).update();
        CardView view = require(ctx.agentId(), id);
        hub.publish(ctx.roomId(), EventType.CHAT_CARD, ctx.agentId().value(), view);
        return view;
    }

    /** v0.0.19 🍊 Records a human's answer (first answer wins) and notifies the purpose handler. */
    public CardView answer(String cardId, String userId, List<String> optionIds, String otherText) {
        AgentId owner = ownerOf(cardId);
        Row row = find(owner, cardId).orElseThrow(() -> new NotFoundException("Unknown card " + cardId + "."));
        UserView user = users.require(userId);
        if (!user.roomId().equals(row.roomId())) {
            throw new PermissionDeniedException("Only members of the room can answer this card.");
        }
        List<String> chosen = optionIds == null ? List.of() : optionIds;
        String other = otherText == null || otherText.isBlank() ? null : otherText.strip();
        List<String> labels = new ArrayList<>();
        for (String optionId : chosen) {
            labels.add(row.options().stream().filter(o -> o.id().equals(optionId)).findFirst()
                    .orElseThrow(() -> new BadRequestException("Unknown option " + optionId + ".")).label());
        }
        if (other != null && !row.allowOther()) {
            throw new BadRequestException("This card does not accept free-text answers.");
        }
        if (labels.isEmpty() && other == null) {
            throw new BadRequestException("Pick an option or type an answer.");
        }
        Instant now = time.nowInstant();
        int updated = scoped(SQL_ANSWER, owner).param("id", cardId)
                .param("answer", jsons.write(new CardView.Answer(chosen, other))).param("by", userId)
                .param("byName", user.username()).param("at", DbTime.toDb(now)).update();
        if (updated == 0) {
            throw new ConflictException("This card was already answered or closed.");
        }
        CardView view = require(owner, cardId);
        hub.publish(row.roomId(), EventType.CHAT_CARD, owner.value(), view);
        CardAnswer answer = new CardAnswer(cardId, owner.value(), row.roomId(), row.purpose(), row.prompt(), labels,
                other, userId, user.username(), row.payload(), null);
        handlers.orderedStream().filter(h -> h.purpose().equals(row.purpose()))
                .forEach(h -> runner.run("card-answer", owner.value(), () -> h.onAnswer(answer)));
        return view;
    }

    /** v0.0.19 🍊 A card by id. */
    public CardView require(AgentId owner, String cardId) {
        return find(owner, cardId).map(this::toView)
                .orElseThrow(() -> new NotFoundException("Unknown card " + cardId + "."));
    }

    /** v0.0.19 🍊 Open and recent cards of a room (bootstrap). */
    public List<CardView> recent(String roomId, int limit) {
        return jdbc.sql("""
                        SELECT agent_id, id, room_id, kind, purpose, message_id, prompt, options, allow_other, payload,
                            status, answer, answered_by_name, created_at, answered_at
                        FROM question_card WHERE room_id = :room ORDER BY created_at DESC LIMIT :limit
                        """).param("room", roomId).param("limit", limit).query(mapper).list()
                .stream().map(this::toView).toList();
    }

    /** v0.0.19 🍊 Owner agent of a card id (the id embeds the agent hex; the table lookup confirms it). */
    private AgentId ownerOf(String cardId) {
        return jdbc.sql("SELECT agent_id FROM question_card WHERE id = :id").param("id", cardId).query(String.class)
                .optional().map(AgentId::of).orElseThrow(() -> new NotFoundException("Unknown card " + cardId + "."));
    }

    /** v0.0.19 🍊 Loads a card row. */
    private Optional<Row> find(AgentId owner, String cardId) {
        return scoped(SQL_FIND, owner).param("id", cardId).query(mapper).optional();
    }

    /** v0.0.19 🍊 Row → contract view. */
    private CardView toView(Row r) {
        return new CardView(r.id(), r.agentId(), r.roomId(), r.kind(), r.messageId(), r.prompt(), r.options(),
                r.allowOther(), r.status(), r.answer(), r.answeredByName(), time.compact(r.created()),
                r.answered() == null ? null : time.compact(r.answered()));
    }
}
