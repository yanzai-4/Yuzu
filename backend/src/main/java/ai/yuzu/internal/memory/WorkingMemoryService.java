package ai.yuzu.internal.memory;

import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.internal.consciousness.PoolMessage;
import ai.yuzu.internal.consciousness.PoolRenderer;
import ai.yuzu.llm.prompt.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * v0.0.13 🍊 Working memory: a copy of the main consciousness's inputs and outputs, with batched compaction.
 *
 * <ul>
 *   <li>Only the main loop writes it. Recording is idempotent (unique origin reference), and the main
 *       consciousness's own THINK message is never stored twice: it is recorded once as an OUT entry, and
 *       skipped when it comes back as an input ({@code emittedByMain}).</li>
 *   <li>Cache-friendly compaction: when 20 or more verbatim entries exist, the oldest ones are folded into the
 *       rolling digest by the {@link WorkingMemoryCompactor} and 10 stay verbatim. Between compactions the
 *       rendered text only grows by appends. Compacted rows stay in MySQL for time-range recall.</li>
 *   <li>The per-agent view is cached in memory and swapped after every change.</li>
 * </ul>
 */
@Service
public class WorkingMemoryService {

    /** v0.0.13 🍊 Verbatim entries kept after a compaction. */
    public static final int KEEP_VERBATIM = 10;
    /** v0.0.13 🍊 Verbatim entries that trigger a compaction. */
    public static final int COMPACT_AT = 20;

    private static final Logger log = LoggerFactory.getLogger(WorkingMemoryService.class);

    private final WorkingMemoryRepository repository;
    private final PoolRenderer renderer;
    private final TokenEstimator tokens;
    private final NaturalTime time;
    private final AsyncRunner runner;
    private final TransactionTemplate tx;
    private final ObjectProvider<WorkingMemoryCompactor> compactor;
    private final Map<AgentId, Slot> slots = new ConcurrentHashMap<>();

    /** v0.0.13 🍊 Injects collaborators; the compactor is optional and resolved lazily. */
    public WorkingMemoryService(WorkingMemoryRepository repository, PoolRenderer renderer, TokenEstimator tokens,
                                NaturalTime time, AsyncRunner runner, TransactionTemplate tx,
                                ObjectProvider<WorkingMemoryCompactor> compactor) {
        this.repository = repository;
        this.renderer = renderer;
        this.tokens = tokens;
        this.time = time;
        this.runner = runner;
        this.tx = tx;
        this.compactor = compactor;
    }

    /** v0.0.13 🍊 Records the inputs of a main run (skips the run's own THINK messages already stored as outputs). */
    public void recordInputs(AgentId agentId, String runId, List<PoolMessage> batch) {
        Slot slot = slot(agentId);
        slot.lock.lock();
        try {
            for (PoolMessage m : batch) {
                if (m.emittedByMain()) {
                    continue;
                }
                repository.insertIfAbsent(agentId, runId, WorkingMemoryEntry.Direction.IN, renderer.label(m), m.id(),
                        m.text(), tokens.count(m.text()), m.createdAt()).ifPresent(slot.entries::add);
            }
        } finally {
            slot.lock.unlock();
        }
        maybeCompact(agentId, slot);
    }

    /** v0.0.13 🍊 Records the output (thought, decision, actions) of a main run. */
    public void recordOutput(AgentId agentId, String runId, String text) {
        Slot slot = slot(agentId);
        slot.lock.lock();
        try {
            repository.insertIfAbsent(agentId, runId, WorkingMemoryEntry.Direction.OUT, "me (my decision)", runId,
                    text, tokens.count(text), time.nowInstant()).ifPresent(slot.entries::add);
        } finally {
            slot.lock.unlock();
        }
        maybeCompact(agentId, slot);
    }

    /** v0.0.13 🍊 Prompt text: digest first, then verbatim entries with absolute times (append-only between compactions). */
    public String render(AgentId agentId) {
        Slot slot = slot(agentId);
        slot.lock.lock();
        try {
            StringBuilder sb = new StringBuilder();
            if (!slot.digest.isBlank()) {
                sb.append("Earlier (summarized): ").append(slot.digest.strip()).append("\n");
            }
            for (WorkingMemoryEntry e : slot.entries) {
                sb.append('[').append(time.compact(e.createdAt())).append("] ")
                        .append(e.direction() == WorkingMemoryEntry.Direction.IN ? "IN from " : "OUT ")
                        .append(e.source()).append(": ").append(e.text().strip()).append('\n');
            }
            return sb.isEmpty() ? "(empty)" : sb.toString().strip();
        } finally {
            slot.lock.unlock();
        }
    }

    /** v0.0.13 🍊 UI view (contract type {@code WorkingMemoryView}). */
    public WorkingMemoryView view(AgentId agentId) {
        Slot slot = slot(agentId);
        slot.lock.lock();
        try {
            List<WorkingMemoryView.Entry> entries = slot.entries.stream()
                    .map(e -> new WorkingMemoryView.Entry(e.id(), e.direction().name(), e.source(), e.text(),
                            time.compact(e.createdAt())))
                    .toList();
            return new WorkingMemoryView(agentId.value(), slot.digest.isBlank() ? null : slot.digest, entries);
        } finally {
            slot.lock.unlock();
        }
    }

    /** v0.0.13 🍊 Number of verbatim entries (tests, diagnostics). */
    public int verbatimCount(AgentId agentId) {
        Slot slot = slot(agentId);
        slot.lock.lock();
        try {
            return slot.entries.size();
        } finally {
            slot.lock.unlock();
        }
    }

    /** v0.0.13 🍊 Runs a compaction synchronously when due; single-flight per agent (skips if one is running). */
    public void compactNow(AgentId agentId) {
        Slot slot = slot(agentId);
        if (!slot.compacting.compareAndSet(false, true)) {
            return;
        }
        try {
            doCompact(agentId, slot);
        } finally {
            slot.compacting.set(false);
        }
    }

    /** v0.0.13 🍊 Schedules one asynchronous compaction per agent at a time when the threshold is reached. */
    private void maybeCompact(AgentId agentId, Slot slot) {
        if (slot.entries.size() < COMPACT_AT || compactor.getIfAvailable() == null
                || !slot.compacting.compareAndSet(false, true)) {
            return;
        }
        runner.run("wm-compaction", agentId.value(), () -> {
            try {
                doCompact(agentId, slot);
            } catch (RuntimeException e) {
                log.warn("Working-memory compaction failed for {} (entries kept verbatim): {}", agentId, e.getMessage());
                throw e;
            } finally {
                slot.compacting.set(false);
            }
        });
    }

    /** v0.0.13 🍊 Folds the oldest entries into the digest (caller holds the single-flight flag). */
    private void doCompact(AgentId agentId, Slot slot) {
        WorkingMemoryCompactor impl = compactor.getIfAvailable();
        if (impl == null) {
            return;
        }
        List<WorkingMemoryEntry> fold;
        String previous;
        slot.lock.lock();
        try {
            if (slot.entries.size() < COMPACT_AT) {
                return;
            }
            fold = List.copyOf(slot.entries.subList(0, slot.entries.size() - KEEP_VERBATIM));
            previous = slot.digest;
        } finally {
            slot.lock.unlock();
        }
        String digest = impl.compact(agentId, previous, fold);
        long through = fold.getLast().seq();
        tx.executeWithoutResult(status -> {
            repository.saveDigest(agentId, digest, through, time.nowInstant());
            repository.markCompacted(agentId, through);
        });
        slot.lock.lock();
        try {
            slot.digest = digest;
            slot.entries.removeIf(e -> e.seq() <= through);
        } finally {
            slot.lock.unlock();
        }
    }

    /** v0.0.13 🍊 Loads (once) the in-memory view of an agent. */
    private Slot slot(AgentId agentId) {
        return slots.computeIfAbsent(agentId, id -> {
            Slot slot = new Slot();
            slot.entries.addAll(repository.verbatim(id));
            slot.digest = repository.digest(id);
            return slot;
        });
    }

    /** v0.0.13 🍊 Cached per-agent state guarded by its own lock. */
    private static final class Slot {
        private final ReentrantLock lock = new ReentrantLock();
        private final List<WorkingMemoryEntry> entries = new ArrayList<>();
        private final AtomicBoolean compacting = new AtomicBoolean();
        private String digest = "";
    }
}
