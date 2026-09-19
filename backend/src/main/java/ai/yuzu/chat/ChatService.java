package ai.yuzu.chat;

import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.room.RoomDirectory;
import ai.yuzu.room.UserView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * v0.0.5 🍊 The single write path of the group chat: persist → window → SSE → agent fan-out.
 *
 * <p>Posting is serialized per room (short lock around insert + window append + publish) so the room
 * sequence, the in-memory window and the realtime stream always agree on message order. Listeners run
 * asynchronously and never delay the poster.</p>
 */
@Service
public class ChatService {

    private static final int MAX_LENGTH = 8_000;

    private final ChatMessageRepository repository;
    private final RoomWindow window;
    private final RoomDirectory directory;
    private final HumanUserService users;
    private final SseHub hub;
    private final NaturalTime time;
    private final AsyncRunner runner;
    private final ObjectProvider<ChatMessageListener> listeners;
    private final Map<String, ReentrantLock> roomLocks = new ConcurrentHashMap<>();

    /** v0.0.5 🍊 Injects collaborators; listeners are lazy because agents depend on the chat service. */
    public ChatService(ChatMessageRepository repository, RoomWindow window, RoomDirectory directory,
                       HumanUserService users, SseHub hub, NaturalTime time, AsyncRunner runner,
                       ObjectProvider<ChatMessageListener> listeners) {
        this.repository = repository;
        this.window = window;
        this.directory = directory;
        this.users = users;
        this.hub = hub;
        this.time = time;
        this.runner = runner;
        this.listeners = listeners;
    }

    /** v0.0.5 🍊 Posts a message typed by a human in the composer. */
    public ChatMessage postHuman(String roomId, String userId, String content) {
        UserView user = users.require(userId);
        if (!user.roomId().equals(roomId)) {
            throw new PermissionDeniedException("User " + userId + " is not a member of " + roomId + ".");
        }
        return post(ChatPost.human(roomId, userId, user.username(), content));
    }

    /** v0.0.5 🍊 Posts any message (humans, agents, system); parses mentions unless overridden. */
    public ChatMessage post(ChatPost post) {
        String content = post.content() == null ? "" : post.content().strip();
        if (content.isEmpty() && post.streamState() != StreamState.STREAMING) {
            throw new BadRequestException("A message cannot be empty.");
        }
        if (content.length() > MAX_LENGTH) {
            throw new BadRequestException("A message can have at most " + MAX_LENGTH + " characters.");
        }
        MentionParser.Mentions mentions = post.mentionTargetsOverride() != null
                ? new MentionParser.Mentions(post.mentionTargetsOverride(), false)
                : MentionParser.parse(content, directory.members(post.roomId()));
        List<String> targets = mentions.memberIds().stream().filter(id -> !id.equals(post.authorId())).toList();
        AgentId owner = post.authorKind() == AuthorKind.AGENT ? AgentId.of(post.authorId()) : AgentId.SYSTEM;

        ReentrantLock lock = roomLocks.computeIfAbsent(post.roomId(), id -> new ReentrantLock());
        ChatMessage saved;
        lock.lock();
        try {
            saved = insertWithFreshId(post, owner, content, targets, mentions.all());
            window.append(saved);
            hub.publish(saved.roomId(), EventType.CHAT_MESSAGE,
                    saved.authorKind() == AuthorKind.AGENT ? saved.authorId() : null, saved.toView(time));
        } finally {
            lock.unlock();
        }
        if (saved.fanout()) {
            listeners.orderedStream().forEach(listener ->
                    runner.run("chat-fanout", null, () -> listener.onMessage(saved)));
        }
        return saved;
    }

    /** v0.0.5 🍊 Replaces a message's content (streaming deltas and the final text) and publishes it. */
    public ChatMessage updateContent(ChatMessage message, String content, StreamState state, boolean persist) {
        ChatMessage updated = message.withContent(content, state);
        window.replace(updated);
        if (persist) {
            repository.updateContent(message.roomId(), message.id(), content, state);
        }
        hub.publish(message.roomId(), EventType.CHAT_MESSAGE,
                message.authorKind() == AuthorKind.AGENT ? message.authorId() : null, updated.toView(time));
        return updated;
    }

    /** v0.0.5 🍊 Recent messages (from memory) or older history (from MySQL), ascending. */
    public List<ChatMessageView> history(String roomId, Long beforeSeq, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<ChatMessage> messages = beforeSeq == null
                ? window.recent(roomId, safeLimit)
                : repository.findBefore(roomId, beforeSeq, safeLimit);
        return messages.stream().map(m -> m.toView(time)).toList();
    }

    /** v0.0.5 🍊 Builds, inserts and returns the saved message, retrying on a duplicate random id. */
    private ChatMessage insertWithFreshId(ChatPost post, AgentId owner, String content, List<String> mentions,
                                         boolean mentionAll) {
        DuplicateKeyException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            ChatMessage draft = new ChatMessage(IdGen.recordId(DataName.MESSAGE, owner), post.roomId(), 0,
                    post.authorKind(), post.authorId(), post.authorName(), post.kind(), content, mentions,
                    mentionAll, post.closure(), post.causalDepth(), post.replyTo(), post.cardId(), post.fanout(),
                    post.streamState(), post.traceId(), time.nowInstant());
            try {
                long seq = repository.insert(draft);
                return new ChatMessage(draft.id(), draft.roomId(), seq, draft.authorKind(), draft.authorId(),
                        draft.authorName(), draft.kind(), draft.content(), draft.mentions(), draft.mentionAll(),
                        draft.closure(), draft.causalDepth(), draft.replyTo(), draft.cardId(), draft.fanout(),
                        draft.streamState(), draft.traceId(), draft.createdAt());
            } catch (DuplicateKeyException e) {
                last = e;
            }
        }
        throw last;
    }
}
