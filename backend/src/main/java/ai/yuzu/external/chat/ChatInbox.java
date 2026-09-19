package ai.yuzu.external.chat;

import ai.yuzu.agent.runtime.AgentComponent;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.id.AgentId;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * v0.0.15 🍊 Per-agent chat inbox: single-flight evaluation with a short debounce.
 *
 * <p>At most one chat-module call is in flight per agent. Normal messages wait {@value #DEBOUNCE_MILLIS} ms
 * (so a quick burst is evaluated once), messages that @mention the agent or @all are evaluated at once.
 * Messages arriving during an evaluation are coalesced into the next one; nothing is lost.</p>
 */
public final class ChatInbox implements AgentComponent {

    static final long DEBOUNCE_MILLIS = 400;

    /** v0.0.15 🍊 Evaluates a batch of new messages for an agent. */
    @FunctionalInterface
    public interface Handler {
        /** v0.0.15 🍊 Handles new messages (oldest first). */
        void handle(AgentId agentId, List<ChatMessage> batch);
    }

    private final AgentId agentId;
    private final Handler handler;
    private final ScheduledExecutorService timer;
    private final AsyncRunner runner;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<ChatMessage> pending = new ArrayList<>();
    private boolean evaluating;
    private boolean closed;
    private ScheduledFuture<?> scheduled;
    private long scheduledAt;

    /** v0.0.15 🍊 Creates the inbox of one agent. */
    public ChatInbox(AgentId agentId, Handler handler, ScheduledExecutorService timer, AsyncRunner runner) {
        this.agentId = agentId;
        this.handler = handler;
        this.timer = timer;
        this.runner = runner;
    }

    /** v0.0.15 🍊 Queues a message; urgent messages (mentions) are evaluated without debounce. */
    public void offer(ChatMessage message, boolean urgent) {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            pending.add(message);
            if (!evaluating) {
                schedule(urgent ? 0 : DEBOUNCE_MILLIS);
            }
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.15 🍊 Messages waiting for evaluation (diagnostics). */
    public int pendingCount() {
        lock.lock();
        try {
            return pending.size();
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.15 🍊 Stops accepting messages (retirement). */
    @Override
    public void shutdown() {
        lock.lock();
        try {
            closed = true;
            pending.clear();
            if (scheduled != null) {
                scheduled.cancel(false);
            }
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.15 🍊 (Re)schedules the evaluation unless an earlier one is already due. */
    private void schedule(long delayMillis) {
        long due = System.currentTimeMillis() + delayMillis;
        if (scheduled != null && !scheduled.isDone() && scheduledAt <= due) {
            return;
        }
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        scheduledAt = due;
        scheduled = timer.schedule(() -> runner.run("chat-inbox", agentId.value(), this::evaluate), delayMillis,
                TimeUnit.MILLISECONDS);
    }

    /** v0.0.15 🍊 Takes everything pending and evaluates it; reschedules if more arrived meanwhile. */
    private void evaluate() {
        List<ChatMessage> batch;
        lock.lock();
        try {
            if (evaluating || pending.isEmpty() || closed) {
                return;
            }
            batch = List.copyOf(pending);
            pending.clear();
            evaluating = true;
            scheduled = null;
        } finally {
            lock.unlock();
        }
        try {
            handler.handle(agentId, batch);
        } finally {
            lock.lock();
            try {
                evaluating = false;
                if (!pending.isEmpty() && !closed) {
                    schedule(0);
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
