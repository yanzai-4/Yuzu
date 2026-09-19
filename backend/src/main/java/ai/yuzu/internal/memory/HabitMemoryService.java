package ai.yuzu.internal.memory;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.internal.cognition.HabitIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * v0.0.28 🍊 Habit memory ("how I work"): the only writer is the learning module, the reader is cognition.
 *
 * <p>Every write invalidates the cognition habit index in the same call, so a habit is visible to the next
 * cognition pass immediately. Writes that would collide with the {@code UNIQUE(agent_id, content_hash)} key are
 * reported as duplicates instead of failing the caller.</p>
 */
@Service
public class HabitMemoryService implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(HabitMemoryService.class);

    private final HabitMemoryRepository repository;
    private final HabitIndexService index;
    private final NaturalTime time;

    /** v0.0.28 🍊 Injects collaborators. */
    public HabitMemoryService(HabitMemoryRepository repository, HabitIndexService index, NaturalTime time) {
        this.repository = repository;
        this.index = index;
        this.time = time;
    }

    /** v0.0.28 🍊 Habit memory. */
    @Override
    public MemoryKind kind() {
        return MemoryKind.HABIT;
    }

    /** v0.0.28 🍊 Active habit with this content hash. */
    @Override
    public Optional<StoredMemory> byHash(AgentId agentId, byte[] contentHash) {
        return repository.byHash(agentId, contentHash);
    }

    /** v0.0.28 🍊 Active habit by id. */
    @Override
    public Optional<StoredMemory> byId(AgentId agentId, String id) {
        return repository.byId(agentId, id);
    }

    /** v0.0.28 🍊 Similar habits by FULLTEXT ngram search. */
    @Override
    public List<StoredMemory> similar(AgentId agentId, String query, int limit) {
        return query == null || query.isBlank() ? List.of() : repository.similar(agentId, query, limit);
    }

    /** v0.0.28 🍊 Stores a new habit and refreshes the cognition index. */
    @Override
    public Optional<String> insert(AgentId agentId, MemoryCandidate candidate) {
        try {
            String id = repository.insert(agentId, candidate, MemoryHash.of(candidate), time.nowInstant());
            index.invalidate(agentId);
            return Optional.of(id);
        } catch (DuplicateKeyException e) {
            log.debug("Habit of {} already stored with the same content hash", agentId);
            return Optional.empty();
        }
    }

    /** v0.0.28 🍊 Replaces a habit's technique with the merged text. */
    @Override
    public void merge(AgentId agentId, String targetId, String mergedText) {
        StoredMemory target = repository.byId(agentId, targetId).orElse(null);
        if (target == null) {
            return;
        }
        MemoryCandidate merged = MemoryCandidate.habit(target.title(), target.scenario(), mergedText);
        try {
            repository.updateTechnique(agentId, targetId, merged.body(), MemoryHash.of(merged), time.nowInstant());
        } catch (DuplicateKeyException e) {
            log.debug("Merged habit of {} equals another habit; keeping both unchanged", agentId);
            return;
        }
        index.invalidate(agentId);
    }

    /** v0.0.28 🍊 Supersedes a habit and stores the candidate in its place. */
    @Override
    public Optional<String> overwrite(AgentId agentId, String targetId, MemoryCandidate candidate) {
        repository.supersede(agentId, targetId, time.nowInstant());
        Optional<String> id = insert(agentId, candidate);
        index.invalidate(agentId);
        return id;
    }

    /** v0.0.28 🍊 Every active habit (cognition, diagnostics). */
    public List<StoredMemory> active(AgentId agentId) {
        return repository.active(agentId);
    }
}
