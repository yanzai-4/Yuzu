package ai.yuzu.chat;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * v0.0.5 🍊 In-memory window of each room's latest messages (authoritative for recent reads).
 *
 * <p>Every agent's chat module reads the window on every message, so reads never hit MySQL. The window
 * is loaded lazily once per room and then maintained by the single {@link ChatService} write path.
 * Each message gets a per-room ordinal; the anchored context starts at an ordinal that only moves every
 * 10 messages, so the rendered prompt grows by appends and stays cache-friendly even when the window
 * itself is full and sliding.</p>
 */
@Component
public class RoomWindow {

    static final int CAPACITY = 200;
    private static final int BASE = 20;
    private static final int STEP = 10;

    private final ChatMessageRepository repository;
    private final Map<String, Slot> rooms = new ConcurrentHashMap<>();

    /** v0.0.5 🍊 Injects the repository used for the initial load. */
    public RoomWindow(ChatMessageRepository repository) {
        this.repository = repository;
    }

    /** v0.0.5 🍊 Appends a new message (called only by ChatService after persisting). */
    public void append(ChatMessage message) {
        Slot slot = slot(message.roomId());
        slot.lock.writeLock().lock();
        try {
            if (slot.entries.size() == CAPACITY) {
                slot.entries.removeFirst();
            }
            slot.entries.addLast(new Entry(++slot.lastOrdinal, message));
        } finally {
            slot.lock.writeLock().unlock();
        }
    }

    /** v0.0.5 🍊 Replaces a message with the same id, keeping its ordinal (streaming updates). */
    public void replace(ChatMessage message) {
        Slot slot = slot(message.roomId());
        slot.lock.writeLock().lock();
        try {
            ArrayDeque<Entry> rebuilt = new ArrayDeque<>(CAPACITY);
            for (Entry e : slot.entries) {
                rebuilt.addLast(e.message.id().equals(message.id()) ? new Entry(e.ordinal, message) : e);
            }
            slot.entries.clear();
            slot.entries.addAll(rebuilt);
        } finally {
            slot.lock.writeLock().unlock();
        }
    }

    /** v0.0.5 🍊 The latest {@code n} messages in ascending order. */
    public List<ChatMessage> recent(String roomId, int n) {
        Slot slot = slot(roomId);
        slot.lock.readLock().lock();
        try {
            List<ChatMessage> result = new ArrayList<>(Math.min(n, slot.entries.size()));
            Iterator<Entry> it = slot.entries.descendingIterator();
            while (it.hasNext() && result.size() < n) {
                result.add(it.next().message);
            }
            Collections.reverse(result);
            return List.copyOf(result);
        } finally {
            slot.lock.readLock().unlock();
        }
    }

    /**
     * v0.0.5 🍊 Cache-friendly context for a message: 20-29 earlier messages whose start moves every 10.
     *
     * @param roomId room
     * @param newest the message being evaluated (excluded from the returned context)
     * @return earlier messages in ascending order (all of them when fewer than 20 exist)
     */
    public List<ChatMessage> anchoredContext(String roomId, ChatMessage newest) {
        Slot slot = slot(roomId);
        slot.lock.readLock().lock();
        try {
            long newestOrdinal = slot.lastOrdinal + 1;
            for (Entry e : slot.entries) {
                if (e.message.id().equals(newest.id())) {
                    newestOrdinal = e.ordinal;
                    break;
                }
            }
            long prior = newestOrdinal - 1;
            long anchor = prior <= BASE ? 0 : ((prior - BASE) / STEP) * STEP;
            List<ChatMessage> context = new ArrayList<>(BASE + STEP);
            for (Entry e : slot.entries) {
                if (e.ordinal > anchor && e.ordinal <= prior) {
                    context.add(e.message);
                }
            }
            return List.copyOf(context);
        } finally {
            slot.lock.readLock().unlock();
        }
    }

    /** v0.0.5 🍊 Finds a recent message by id (null when it left the window). */
    public ChatMessage find(String roomId, String messageId) {
        Slot slot = slot(roomId);
        slot.lock.readLock().lock();
        try {
            for (Entry e : slot.entries) {
                if (e.message.id().equals(messageId)) {
                    return e.message;
                }
            }
            return null;
        } finally {
            slot.lock.readLock().unlock();
        }
    }

    /** v0.0.5 🍊 Returns (loading once) the slot of a room. */
    private Slot slot(String roomId) {
        return rooms.computeIfAbsent(roomId, id -> {
            Slot slot = new Slot();
            for (ChatMessage m : repository.findRecent(id, CAPACITY)) {
                slot.entries.addLast(new Entry(++slot.lastOrdinal, m));
            }
            return slot;
        });
    }

    /** v0.0.5 🍊 A message with its per-room ordinal. */
    private record Entry(long ordinal, ChatMessage message) {
    }

    /** v0.0.5 🍊 Messages of one room guarded by a read/write lock. */
    private static final class Slot {
        private final ArrayDeque<Entry> entries = new ArrayDeque<>(CAPACITY);
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private long lastOrdinal;
    }
}
