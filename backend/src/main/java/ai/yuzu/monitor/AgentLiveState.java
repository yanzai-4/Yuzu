package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/** v0.0.12 🍊 Mutable live state of one agent (running spans, flags, counters, recent events) guarded by its own lock. */
final class AgentLiveState {

    private final AgentId agentId;
    private final MonitorTimings timings;
    private final CoalescingTrigger statusTrigger;
    private final CoalescingTrigger summaryTrigger;
    private final ReentrantLock lock = new ReentrantLock();
    private final LinkedHashMap<String, ActiveSpan> spans = new LinkedHashMap<>();
    private final ArrayDeque<ModuleEvent> recent = new ArrayDeque<>();
    private String roomId;
    private boolean paused;
    private boolean waiting;
    private String waitingReason;
    private int poolSize;
    private int pendingBatches;
    private ErrorMark error;
    private SummaryPin pin;
    private long tick;
    private long activity;
    private long summarizedActivity;
    private long busyEpoch;
    private boolean retired;
    private AgentStatusView lastPublished;

    /** v0.0.12 🍊 Creates the state of an agent sitting in a room. */
    AgentLiveState(AgentId agentId, String roomId, boolean paused, MonitorTimings timings,
                   CoalescingTrigger statusTrigger, CoalescingTrigger summaryTrigger) {
        this.agentId = agentId;
        this.roomId = roomId;
        this.paused = paused;
        this.timings = timings;
        this.statusTrigger = statusTrigger;
        this.summaryTrigger = summaryTrigger;
    }

    /** v0.0.12 🍊 Throttled publisher of this agent's agent.status events. */
    CoalescingTrigger statusTrigger() {
        return statusTrigger;
    }

    /** v0.0.12 🍊 Throttled caller of the bubble summarizer. */
    CoalescingTrigger summaryTrigger() {
        return summaryTrigger;
    }

    /** v0.0.12 🍊 Room the agent sits in. */
    String roomId() {
        lock.lock();
        try {
            return roomId;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Applies one reported event (quiet modules are ignored); tells the board what changed. */
    Effect apply(ModuleEvent event, DeskState desk, long nowNanos) {
        lock.lock();
        try {
            if (retired || event.module().quiet()) {
                return Effect.NONE;
            }
            DeskState declared = desk == null ? event.module().desk() : desk;
            boolean failed = false;
            boolean changed = false;
            switch (event.phase()) {
                case START, STATE -> {
                    if (declared == DeskState.IDLE) {
                        changed = drop(event.spanId());
                    } else {
                        changed = upsert(event.spanId(), event.module(), declared, event.text());
                    }
                }
                case END, CANCELLED -> changed = drop(event.spanId());
                case ERROR -> {
                    drop(event.spanId());
                    error = new ErrorMark(event.module(), event.text(), nowNanos + timings.errorHold().toNanos());
                    changed = true;
                    failed = true;
                }
                case INFO -> {
                    // One-off events change no span; they still feed the summarizer.
                }
            }
            recent.addLast(event);
            while (recent.size() > timings.recentEvents()) {
                recent.removeFirst();
            }
            if (changed) {
                activity++;
            }
            return new Effect(changed, !spans.isEmpty(), failed);
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 A running span declared another desk state (IDLE removes it from the desk). */
    Effect redesk(String spanId, ModuleKind module, DeskState desk, String text) {
        lock.lock();
        try {
            if (retired || module.quiet() || spanId == null) {
                return Effect.NONE;
            }
            boolean changed = desk == DeskState.IDLE
                    ? drop(spanId)
                    : upsert(spanId, module, desk, text);
            if (!changed) {
                return Effect.NONE;
            }
            activity++;
            return new Effect(true, !spans.isEmpty(), false);
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Sets room and pause flag (registration); true when the pause flag changed. */
    boolean place(String room, boolean nowPaused) {
        lock.lock();
        try {
            if (room != null) {
                roomId = room;
            }
            boolean changed = paused != nowPaused;
            paused = nowPaused;
            return changed;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Sets the pause flag; true when it changed. */
    boolean setPaused(boolean nowPaused) {
        return place(null, nowPaused);
    }

    /** v0.0.12 🍊 Sets the waiting flag and reason; true when either changed. */
    boolean setWaiting(boolean nowWaiting, String reason) {
        lock.lock();
        try {
            String nextReason = nowWaiting && reason != null && !reason.isBlank() ? reason.strip() : null;
            boolean changed = waiting != nowWaiting || !Objects.equals(waitingReason, nextReason);
            waiting = nowWaiting;
            waitingReason = nextReason;
            return changed;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Sets the pool size (negative becomes 0); true when it changed. */
    boolean setPoolSize(int size) {
        lock.lock();
        try {
            int next = Math.max(0, size);
            boolean changed = poolSize != next;
            poolSize = next;
            return changed;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Sets the pending batch count (negative becomes 0); true when it changed. */
    boolean setPendingBatches(int count) {
        lock.lock();
        try {
            int next = Math.max(0, count);
            boolean changed = pendingBatches != next;
            pendingBatches = next;
            return changed;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Forgets everything and stops the triggers (the agent was retired). */
    void retire() {
        lock.lock();
        try {
            retired = true;
            spans.clear();
            recent.clear();
        } finally {
            lock.unlock();
        }
        statusTrigger.close();
        summaryTrigger.close();
    }

    /** v0.0.12 🍊 Current status with the given natural-language time. */
    AgentStatusView view(String time, long nowNanos) {
        lock.lock();
        try {
            return derive(time, nowNanos);
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Status to publish, or null when retired, roomless or identical (ignoring time) to the last one published. */
    AgentStatusView publication(String time, long nowNanos) {
        lock.lock();
        try {
            if (retired || roomId == null) {
                return null;
            }
            AgentStatusView view = derive(time, nowNanos);
            AgentStatusView key = new AgentStatusView(view.agentId(), view.state(), view.activeModules(), view.bubble(),
                    view.poolSize(), view.pendingBatches(), "");
            if (key.equals(lastPublished)) {
                return null;
            }
            lastPublished = key;
            return view;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Forgets the last publication so the next one goes out even if unchanged (periodic refresh). */
    void forgetPublication() {
        lock.lock();
        try {
            lastPublished = null;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Input for the summarizer, or null when idle, retired or unchanged since the last summary. */
    SummaryRequest summaryRequest() {
        lock.lock();
        try {
            ActiveSpan focus = StatusDeriver.focus(spans.values());
            if (retired || focus == null || activity == summarizedActivity) {
                return null;
            }
            summarizedActivity = activity;
            return new SummaryRequest(List.copyOf(recent), StatusDeriver.defaultSummary(focus), focus.module(),
                    busyEpoch);
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Pins a summary that adds something to the code default; true when the shown bubble may change. */
    boolean acceptSummary(SummaryRequest request, String summary) {
        lock.lock();
        try {
            if (retired || request.epoch() != busyEpoch) {
                return false;
            }
            String text = BubbleText.summary(summary, "");
            SummaryPin next = text.isEmpty() || text.equals(request.defaultText())
                    ? null : new SummaryPin(text, request.module(), request.epoch());
            boolean changed = !Objects.equals(next, pin);
            pin = next;
            return changed;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.12 🍊 Derives the status from the current fields (lock held). */
    private AgentStatusView derive(String time, long nowNanos) {
        if (error != null && nowNanos - error.untilNanos() >= 0) {
            error = null;
        }
        ActiveSpan focus = StatusDeriver.focus(spans.values());
        DeskState state = StatusDeriver.state(error != null, paused, waiting, focus);
        AgentStatusView.Bubble bubble = StatusDeriver.bubble(state, error == null ? null : error.module(),
                error == null ? null : error.text(), waiting, waitingReason, focus,
                focus == null ? null : summaryOf(focus));
        return new AgentStatusView(agentId.value(), state.name(), StatusDeriver.activeModules(spans.values()), bubble,
                poolSize, pendingBatches, time);
    }

    /** v0.0.12 🍊 The pinned summary when it belongs to this busy period and focus module, else the code default. */
    private String summaryOf(ActiveSpan focus) {
        if (pin != null && pin.epoch() == busyEpoch && pin.module() == focus.module()) {
            return pin.text();
        }
        return StatusDeriver.defaultSummary(focus);
    }

    /** v0.0.12 🍊 Adds or replaces a running span; identical STATE events leave the desk and summary cadence unchanged. */
    private boolean upsert(String spanId, ModuleKind module, DeskState desk, String text) {
        if (spanId == null) {
            return false;
        }
        ActiveSpan current = spans.get(spanId);
        if (current != null && current.module() == module && current.desk() == desk
                && Objects.equals(current.text(), text)) {
            return false;
        }
        if (spans.isEmpty()) {
            busyEpoch++;
            pin = null;
        }
        spans.put(spanId, new ActiveSpan(spanId, module, desk, text, ++tick));
        if (spans.size() > timings.maxSpans()) {
            Iterator<String> oldest = spans.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        return true;
    }

    /** v0.0.12 🍊 Removes a running span; the busy period ends when none is left (lock held). */
    private boolean drop(String spanId) {
        if (spanId == null || spans.remove(spanId) == null) {
            return false;
        }
        if (spans.isEmpty()) {
            pin = null;
        }
        return true;
    }

    /** v0.0.12 🍊 What an update changed: anything, whether the agent is busy, whether a failure was flagged. */
    record Effect(boolean changed, boolean busy, boolean failed) {

        /** v0.0.12 🍊 Nothing changed. */
        static final Effect NONE = new Effect(false, false, false);
    }

    /** v0.0.12 🍊 Summarizer input captured under the lock. */
    record SummaryRequest(List<ModuleEvent> events, String defaultText, ModuleKind module, long epoch) {
    }

    /** v0.0.12 🍊 A failure keeping the desk in ERROR until {@code untilNanos}. */
    private record ErrorMark(ModuleKind module, String text, long untilNanos) {
    }

    /** v0.0.12 🍊 A summarizer text shown while the same busy period and focus module last. */
    private record SummaryPin(String text, ModuleKind module, long epoch) {
    }
}
