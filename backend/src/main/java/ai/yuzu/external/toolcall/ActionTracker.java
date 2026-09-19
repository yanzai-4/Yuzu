package ai.yuzu.external.toolcall;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.runtime.AgentComponent;
import ai.yuzu.agent.runtime.AgentComponentFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** v0.0.18 🍊 Per-agent view of action batches in progress and consecutive behavior rejections. */
public final class ActionTracker implements AgentComponent {

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, List<String>> pending = new LinkedHashMap<>();
    private final AtomicInteger rejections = new AtomicInteger();

    /** v0.0.18 🍊 Registers a batch as in progress. */
    public void started(String batchId, List<String> actions) {
        lock.lock();
        try {
            pending.put(batchId, List.copyOf(actions));
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.18 🍊 Removes a finished batch. */
    public void finished(String batchId) {
        lock.lock();
        try {
            pending.remove(batchId);
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.18 🍊 Number of batches in progress. */
    public int pendingCount() {
        lock.lock();
        try {
            return pending.size();
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.18 🍊 Rendered list of actions in progress ("(none)" when idle). */
    public String render() {
        lock.lock();
        try {
            if (pending.isEmpty()) {
                return "(none)";
            }
            StringBuilder sb = new StringBuilder();
            pending.values().forEach(actions -> actions.forEach(a -> sb.append("\n- ").append(a)));
            return sb.toString();
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.18 🍊 Counts a rejection and returns how many happened in a row. */
    public int rejected() {
        return rejections.incrementAndGet();
    }

    /** v0.0.18 🍊 Resets the rejection streak (after an approved batch or an escalation). */
    public void approved() {
        rejections.set(0);
    }

    /** v0.0.18 🍊 Creates one tracker per agent. */
    @Component
    public static class Factory implements AgentComponentFactory<ActionTracker> {

        /** v0.0.18 🍊 Component type. */
        @Override
        public Class<ActionTracker> type() {
            return ActionTracker.class;
        }

        /** v0.0.18 🍊 Creates the tracker. */
        @Override
        public ActionTracker create(AgentProfile profile) {
            return new ActionTracker();
        }
    }
}
