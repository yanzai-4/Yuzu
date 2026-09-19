package ai.yuzu.internal.memory;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * v0.0.28 🍊 The one write flow both long-term memories share (S30 and S31 are the same five steps).
 *
 * <ol>
 *   <li>content hash — an exact repeat is dropped without asking any model;</li>
 *   <li>FULLTEXT ngram search for similar entries — nothing similar means the entry is simply new, again
 *       without a model call;</li>
 *   <li>{@link MemoryJudgeModule} decides NEW / DUPLICATE / CONFLICT;</li>
 *   <li>DUPLICATE is merged, overwritten or ignored; NEW is inserted;</li>
 *   <li>CONFLICT never writes: the old entry is kept and the contradiction waits
 *       {@value #HOLD_ROUNDS} subconscious rounds for evidence.</li>
 * </ol>
 *
 * <p>The stores invalidate their own readers' caches inside their write methods, so a habit is in the cognition
 * index and a deep memory is recallable the moment this method returns.</p>
 */
@Service
public class MemoryWriter {

    /** v0.0.28 🍊 Similar entries shown to the judge. */
    static final int SIMILAR_LIMIT = 5;

    /** v0.0.28 🍊 Subconscious rounds a contradiction is held before the old memory wins by default. */
    public static final int HOLD_ROUNDS = 10;

    private static final Logger log = LoggerFactory.getLogger(MemoryWriter.class);

    private final MemoryJudgeModule judge;
    private final MemoryConflictRepository conflicts;
    private final NaturalTime time;

    /** v0.0.28 🍊 Injects collaborators. */
    public MemoryWriter(MemoryJudgeModule judge, MemoryConflictRepository conflicts, NaturalTime time) {
        this.judge = judge;
        this.conflicts = conflicts;
        this.time = time;
    }

    /** v0.0.28 🍊 Offers a candidate to a memory and reports what happened to it. */
    public MemoryOutcome write(AgentContext ctx, MemoryStore store, MemoryCandidate candidate) {
        AgentId agentId = ctx.agentId();
        MemoryKind kind = store.kind();
        if (!candidate.isUsable()) {
            return MemoryOutcome.ignored("There was nothing concrete enough to remember.");
        }
        Optional<StoredMemory> exact = store.byHash(agentId, MemoryHash.of(candidate));
        if (exact.isPresent()) {
            return MemoryOutcome.duplicate(kind, exact.get().id(), exact.get().title());
        }
        List<StoredMemory> similar = store.similar(agentId, candidate.searchText(), SIMILAR_LIMIT);
        if (similar.isEmpty()) {
            return insert(store, ctx, candidate);
        }
        MemoryJudgement judgement = judge.run(ctx, new MemoryJudgeModule.Input(kind, candidate, similar));
        return switch (judgement.verdict()) {
            case NEW -> insert(store, ctx, candidate);
            case DUPLICATE -> applyDuplicate(ctx, store, candidate, judgement, similar);
            case CONFLICT -> hold(ctx, store, candidate, judgement, similar);
        };
    }

    /** v0.0.28 🍊 Stores the candidate (a hash race reads as a duplicate). */
    private MemoryOutcome insert(MemoryStore store, AgentContext ctx, MemoryCandidate candidate) {
        return store.insert(ctx.agentId(), candidate)
                .map(id -> MemoryOutcome.created(store.kind(), id, candidate.title()))
                .orElseGet(() -> MemoryOutcome.duplicate(store.kind(), null, candidate.title()));
    }

    /** v0.0.28 🍊 MERGE folds the candidate in, OVERWRITE supersedes the old entry, IGNORE keeps what exists. */
    private MemoryOutcome applyDuplicate(AgentContext ctx, MemoryStore store, MemoryCandidate candidate,
                                         MemoryJudgement judgement, List<StoredMemory> similar) {
        StoredMemory target = pick(similar, judgement.targetId());
        if (target == null) {
            log.debug("Judge of {} named an unknown entry {}; storing the candidate instead", ctx.agentId(),
                    judgement.targetId());
            return insert(store, ctx, candidate);
        }
        return switch (judgement.action()) {
            case MERGE -> {
                store.merge(ctx.agentId(), target.id(), judgement.mergedText());
                yield MemoryOutcome.merged(store.kind(), target.id(), target.title());
            }
            case OVERWRITE -> {
                String id = store.overwrite(ctx.agentId(), target.id(), candidate).orElse(target.id());
                yield MemoryOutcome.overwritten(store.kind(), id, candidate.title());
            }
            case IGNORE -> MemoryOutcome.duplicate(store.kind(), target.id(), target.title());
        };
    }

    /** v0.0.28 🍊 Records the contradiction and keeps the old entry untouched. */
    private MemoryOutcome hold(AgentContext ctx, MemoryStore store, MemoryCandidate candidate,
                               MemoryJudgement judgement, List<StoredMemory> similar) {
        StoredMemory target = pick(similar, judgement.targetId());
        if (target == null) {
            return insert(store, ctx, candidate);
        }
        String reason = judgement.conflictReason() == null || judgement.conflictReason().isBlank()
                ? "it says the opposite of what I remember" : judgement.conflictReason().strip();
        String id = conflicts.open(ctx.agentId(), store.kind(), target, candidate, reason, HOLD_ROUNDS,
                time.nowInstant());
        return MemoryOutcome.conflict(store.kind(), id, HOLD_ROUNDS, reason);
    }

    /** v0.0.28 🍊 The entry the judge named, or null when it named one that was not offered. */
    private static StoredMemory pick(List<StoredMemory> similar, String id) {
        return similar.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
    }
}
