package ai.yuzu.internal.memory;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * v0.0.28 🍊 Deep memory ("what I know"): written by the memory module, read on demand by the memory-read tool.
 *
 * <p>Writes go straight to {@code deep_memory}, so a memory written a second ago is already findable by the
 * recall queries (FULLTEXT and time range) — there is no read cache in front of it. Exact repeats are stopped
 * by the {@code UNIQUE(agent_id, content_hash)} key before any model is asked.</p>
 */
@Service
public class DeepMemoryService implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(DeepMemoryService.class);

    private final DeepMemoryRepository repository;
    private final NaturalTime time;

    /** v0.0.28 🍊 Injects collaborators. */
    public DeepMemoryService(DeepMemoryRepository repository, NaturalTime time) {
        this.repository = repository;
        this.time = time;
    }

    /** v0.0.28 🍊 Deep memory. */
    @Override
    public MemoryKind kind() {
        return MemoryKind.DEEP;
    }

    /** v0.0.28 🍊 Active memory with this content hash. */
    @Override
    public Optional<StoredMemory> byHash(AgentId agentId, byte[] contentHash) {
        return repository.byHash(agentId, contentHash);
    }

    /** v0.0.28 🍊 Active memory by id. */
    @Override
    public Optional<StoredMemory> byId(AgentId agentId, String id) {
        return repository.byId(agentId, id);
    }

    /** v0.0.28 🍊 Similar memories by FULLTEXT ngram search. */
    @Override
    public List<StoredMemory> similar(AgentId agentId, String query, int limit) {
        return query == null || query.isBlank() ? List.of() : repository.similar(agentId, query, limit);
    }

    /** v0.0.28 🍊 Stores a new memory. */
    @Override
    public Optional<String> insert(AgentId agentId, MemoryCandidate candidate) {
        try {
            return Optional.of(repository.insert(agentId, candidate, MemoryHash.of(candidate), time.nowInstant()));
        } catch (DuplicateKeyException e) {
            log.debug("Deep memory of {} already stored with the same content hash", agentId);
            return Optional.empty();
        }
    }

    /** v0.0.28 🍊 Replaces a memory's content with the merged text. */
    @Override
    public void merge(AgentId agentId, String targetId, String mergedText) {
        StoredMemory target = repository.byId(agentId, targetId).orElse(null);
        if (target == null) {
            return;
        }
        MemoryCandidate merged = MemoryCandidate.deep(target.title(), mergedText, List.of(), "SUBCONSCIOUS");
        try {
            repository.updateContent(agentId, targetId, merged.body(), MemoryHash.of(merged), time.nowInstant());
        } catch (DuplicateKeyException e) {
            log.debug("Merged deep memory of {} equals another memory; keeping both unchanged", agentId);
        }
    }

    /** v0.0.28 🍊 Supersedes a memory and stores the candidate in its place. */
    @Override
    public Optional<String> overwrite(AgentId agentId, String targetId, MemoryCandidate candidate) {
        repository.supersede(agentId, targetId, time.nowInstant());
        return insert(agentId, candidate);
    }
}
