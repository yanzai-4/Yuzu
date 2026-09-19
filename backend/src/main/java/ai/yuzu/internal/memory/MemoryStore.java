package ai.yuzu.internal.memory;

import ai.yuzu.common.id.AgentId;

import java.util.List;
import java.util.Optional;

/**
 * v0.0.28 🍊 What {@link MemoryWriter} needs from a long-term memory, so habit and deep memory share one flow.
 *
 * <p>Implementations own their table, their caches and their cache invalidation: every write method must leave
 * the readers (cognition index, recall) able to see the change immediately.</p>
 */
public interface MemoryStore {

    /** v0.0.28 🍊 Which memory this store holds. */
    MemoryKind kind();

    /** v0.0.28 🍊 An active entry with exactly this content hash, when there is one. */
    Optional<StoredMemory> byHash(AgentId agentId, byte[] contentHash);

    /** v0.0.28 🍊 An active entry by id. */
    Optional<StoredMemory> byId(AgentId agentId, String id);

    /** v0.0.28 🍊 Active entries that the FULLTEXT ngram index considers similar, best first. */
    List<StoredMemory> similar(AgentId agentId, String query, int limit);

    /** v0.0.28 🍊 Stores a new entry; empty when an identical one already exists (hash race). */
    Optional<String> insert(AgentId agentId, MemoryCandidate candidate);

    /** v0.0.28 🍊 Replaces the body of an existing entry with the merged text (the entry keeps its id). */
    void merge(AgentId agentId, String targetId, String mergedText);

    /** v0.0.28 🍊 Supersedes an entry and stores the candidate in its place; empty when nothing was written. */
    Optional<String> overwrite(AgentId agentId, String targetId, MemoryCandidate candidate);
}
