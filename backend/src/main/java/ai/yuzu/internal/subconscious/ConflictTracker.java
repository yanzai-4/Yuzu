package ai.yuzu.internal.subconscious;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.internal.memory.DeepMemoryService;
import ai.yuzu.internal.memory.HabitMemoryService;
import ai.yuzu.internal.memory.MemoryConflict;
import ai.yuzu.internal.memory.MemoryConflictRepository;
import ai.yuzu.internal.memory.MemoryKind;
import ai.yuzu.internal.memory.MemoryStore;
import ai.yuzu.internal.memory.MemoryWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * v0.0.28 🍊 Keeps unresolved memory conflicts alive for exactly {@value MemoryWriter#HOLD_ROUNDS} subconscious
 * rounds.
 *
 * <p>One activation = one subconscious pass, and it always does the same two things under the agent's
 * {@code conflictLock}: first apply whatever this round settled, then count one round down on everything still
 * open. A countdown that reaches zero expires the conflict and the old memory simply stays — the agent never
 * forgets something just because it once saw the opposite.</p>
 */
@Service
public class ConflictTracker {

    /** v0.0.28 🍊 Subconscious rounds a conflict waits for evidence. */
    public static final int HOLD_ROUNDS = MemoryWriter.HOLD_ROUNDS;

    private static final Logger log = LoggerFactory.getLogger(ConflictTracker.class);

    private final MemoryConflictRepository repository;
    private final HabitMemoryService habits;
    private final DeepMemoryService deep;
    private final NaturalTime time;
    private final Map<AgentId, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** v0.0.28 🍊 Injects collaborators. */
    public ConflictTracker(MemoryConflictRepository repository, HabitMemoryService habits, DeepMemoryService deep,
                           NaturalTime time) {
        this.repository = repository;
        this.habits = habits;
        this.deep = deep;
        this.time = time;
    }

    /** v0.0.28 🍊 Conflicts still waiting for evidence. */
    public List<MemoryConflict> open(AgentId agentId) {
        return repository.open(agentId);
    }

    /** v0.0.28 🍊 The unresolved conflicts as prompt text for the subconscious. */
    public String render(AgentId agentId) {
        List<MemoryConflict> open = open(agentId);
        return open.isEmpty() ? "(none)"
                : open.stream().map(MemoryConflict::describe).collect(Collectors.joining("\n"));
    }

    /** v0.0.28 🍊 One subconscious activation: apply the updates, then count every open conflict one round down. */
    public void activate(AgentContext ctx, List<SubconsciousOutput.ConflictUpdate> updates) {
        ReentrantLock lock = locks.computeIfAbsent(ctx.agentId(), id -> new ReentrantLock());
        lock.lock();
        try {
            if (updates != null) {
                updates.forEach(update -> apply(ctx.agentId(), update));
            }
            repository.countDown(ctx.agentId(), time.nowInstant());
            int expired = repository.expireElapsed(ctx.agentId(), time.nowInstant());
            if (expired > 0) {
                log.info("{} let {} memory conflict(s) expire; the older memories stay", ctx.agentId(), expired);
            }
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.28 🍊 Settles one conflict: keep the old entry, replace it, or store a combined version. */
    private void apply(AgentId agentId, SubconsciousOutput.ConflictUpdate update) {
        if (update == null || update.conflictId() == null) {
            return;
        }
        MemoryConflict conflict = repository.byId(agentId, update.conflictId()).orElse(null);
        if (conflict == null || !"OPEN".equals(conflict.status())) {
            log.debug("{} tried to settle unknown or closed conflict {}", agentId, update.conflictId());
            return;
        }
        MemoryStore store = conflict.kind() == MemoryKind.HABIT ? habits : deep;
        String reason = update.reason() == null || update.reason().isBlank() ? "(no reason given)"
                : update.reason().strip();
        switch (update.resolution()) {
            case KEEP_OLD -> {
            }
            case USE_NEW -> store.overwrite(agentId, conflict.targetId(), conflict.newCopy());
            case MERGE -> {
                if (update.mergedText() == null || update.mergedText().isBlank()) {
                    log.debug("{} asked to merge conflict {} without text; keeping the old memory", agentId,
                            conflict.id());
                } else {
                    store.merge(agentId, conflict.targetId(), update.mergedText());
                }
            }
        }
        repository.resolve(agentId, conflict.id(), update.resolution().name() + ": " + reason, time.nowInstant());
    }
}
