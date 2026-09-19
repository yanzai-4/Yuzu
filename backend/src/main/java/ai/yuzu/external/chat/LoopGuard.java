package ai.yuzu.external.chat;

import ai.yuzu.chat.AuthorKind;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.chat.ChatPost;
import ai.yuzu.chat.ChatService;
import ai.yuzu.common.time.NaturalTime;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * v0.0.15 🍊 Code-level protection against agent chatter loops (overrides "must answer an agent's @mention").
 *
 * <ol>
 *   <li>Causal depth: agent messages {@value #MAX_DEPTH}+ hops away from a human are context only.</li>
 *   <li>Pair limiter: more than {@value #PAIR_LIMIT} mentions between the same two agents within 2 minutes
 *       pauses that pair (one system notice is posted).</li>
 *   <li>@all budget: at most {@value #ALL_REPLY_BUDGET} direct replies to one human @all message.</li>
 *   <li>Room budget: at most {@value #ROOM_BUDGET} agent posts per minute per room.</li>
 * </ol>
 * The structured {@code closure} flag on messages is the first line of defense (see {@link ChatPrefilter}).
 */
@Component
public class LoopGuard {

    /** v0.0.15 🍊 Agent-hop depth from which messages are context only. */
    public static final int MAX_DEPTH = 6;
    static final int PAIR_LIMIT = 6;
    static final int ALL_REPLY_BUDGET = 2;
    static final int ROOM_BUDGET = 30;
    private static final Duration PAIR_WINDOW = Duration.ofMinutes(2);
    private static final Duration ROOM_WINDOW = Duration.ofMinutes(1);

    private final NaturalTime time;
    private final ChatService chat;
    private final Map<String, Window> pairs = new ConcurrentHashMap<>();
    private final Map<String, Window> rooms = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> allReplies = new ConcurrentHashMap<>();
    private final Map<String, Instant> pairNoticeUntil = new ConcurrentHashMap<>();

    /** v0.0.15 🍊 Injects collaborators (chat lazily: the chat service fans out to listeners that use this guard). */
    public LoopGuard(NaturalTime time, @Lazy ChatService chat) {
        this.time = time;
        this.chat = chat;
    }

    /** v0.0.15 🍊 True when the pair (author of the message, agent) is currently paused by the pair limiter. */
    public boolean pairSuppressed(ChatMessage message, String agentId) {
        if (message.authorKind() != AuthorKind.AGENT) {
            return false;
        }
        Window window = pairs.get(pairKey(message.roomId(), message.authorId(), agentId));
        return window != null && window.count(time.nowInstant(), PAIR_WINDOW) > PAIR_LIMIT;
    }

    /**
     * v0.0.15 🍊 Decides whether an agent may post a direct reply to a trigger message; records the post.
     *
     * @return false when a budget is exhausted (the reply must be dropped)
     */
    public boolean allowReply(ChatMessage trigger, String agentId) {
        Instant now = time.nowInstant();
        Window room = rooms.computeIfAbsent(trigger.roomId(), k -> new Window());
        if (room.count(now, ROOM_WINDOW) >= ROOM_BUDGET) {
            return false;
        }
        if (trigger.authorKind() == AuthorKind.HUMAN && trigger.mentionAll() && !trigger.mentions().contains(agentId)) {
            AtomicInteger used = allReplies.computeIfAbsent(trigger.id(), k -> new AtomicInteger());
            if (used.incrementAndGet() > ALL_REPLY_BUDGET) {
                return false;
            }
        }
        if (trigger.authorKind() == AuthorKind.AGENT) {
            String key = pairKey(trigger.roomId(), trigger.authorId(), agentId);
            Window pair = pairs.computeIfAbsent(key, k -> new Window());
            if (pair.count(now, PAIR_WINDOW) > PAIR_LIMIT) {
                announcePause(trigger, agentId, key, now);
                return false;
            }
            pair.add(now);
        }
        room.add(now);
        return true;
    }

    /** v0.0.15 🍊 Counts an agent post that was not a triage reply (main-consciousness posts) in the room budget. */
    public boolean allowAgentPost(String roomId) {
        Instant now = time.nowInstant();
        Window room = rooms.computeIfAbsent(roomId, k -> new Window());
        if (room.count(now, ROOM_WINDOW) >= ROOM_BUDGET) {
            return false;
        }
        room.add(now);
        return true;
    }

    /** v0.0.15 🍊 Posts one system notice per paused pair and window. */
    private void announcePause(ChatMessage trigger, String agentId, String key, Instant now) {
        Instant until = pairNoticeUntil.get(key);
        if (until != null && until.isAfter(now)) {
            return;
        }
        pairNoticeUntil.put(key, now.plus(PAIR_WINDOW));
        chat.post(ChatPost.system(trigger.roomId(), "Loop guard: " + trigger.authorName()
                + " and a coworker have been messaging each other a lot, so they will pause for two minutes. "
                + "A human can @mention them to continue."));
    }

    /** v0.0.15 🍊 Order-independent key of an agent pair in a room. */
    private static String pairKey(String roomId, String a, String b) {
        return a.compareTo(b) < 0 ? roomId + "|" + a + "|" + b : roomId + "|" + b + "|" + a;
    }

    /** v0.0.15 🍊 Sliding window of event times. */
    private static final class Window {
        private final ArrayDeque<Instant> events = new ArrayDeque<>();
        private final ReentrantLock lock = new ReentrantLock();

        void add(Instant when) {
            lock.lock();
            try {
                events.addLast(when);
            } finally {
                lock.unlock();
            }
        }

        int count(Instant now, Duration span) {
            lock.lock();
            try {
                Instant cutoff = now.minus(span);
                while (!events.isEmpty() && events.peekFirst().isBefore(cutoff)) {
                    events.removeFirst();
                }
                return events.size();
            } finally {
                lock.unlock();
            }
        }
    }
}
