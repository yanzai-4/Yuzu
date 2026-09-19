package ai.yuzu.internal.memory;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.llm.ModelTier;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.prompt.SegmentRank;
import ai.yuzu.module.AiModule;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.module.ModuleSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * v0.0.28 🍊 The judge shared by the learning and the memory module (DEFAULT tier).
 *
 * <p>It sees only the candidate and the entries the FULLTEXT search found, never the whole memory — the
 * comparison must stay cheap and must not depend on the agent's other state. Code has already ruled out exact
 * repeats by content hash and only calls the judge when the search returned something, so the judge's real
 * job is the grey zone: partly overlapping entries and outright contradictions.</p>
 */
@Component
public class MemoryJudgeModule extends AiModule<MemoryJudgeModule.Input, MemoryJudgement> {

    /** v0.0.28 🍊 The candidate and the similar entries it has to be compared against. */
    public record Input(MemoryKind kind, MemoryCandidate candidate, List<StoredMemory> similar) {
    }

    private static final ModuleSpec<MemoryJudgement> SPEC = new ModuleSpec<>("MEMORY_JUDGE", "Sorting memories",
            ModelTier.DEFAULT, "memory_judge", MemoryJudgement.class, false);

    /** v0.0.28 🍊 Receives the shared dependencies. */
    public MemoryJudgeModule(ModuleDeps deps) {
        super(deps);
    }

    /** v0.0.28 🍊 Static description. */
    @Override
    protected ModuleSpec<MemoryJudgement> spec() {
        return SPEC;
    }

    /** v0.0.28 🍊 S7: the candidate plus the similar entries (nothing else; the judge needs no agent state). */
    @Override
    protected void compose(AgentContext ctx, Input input, PromptBuilder prompt) {
        String existing = input.similar().stream().map(m -> "- " + m.id() + ": " + m.describe())
                .collect(Collectors.joining("\n"));
        prompt.add(SegmentRank.S7_STIMULUS, "Candidate and what I already remember",
                "Memory: " + input.kind().label()
                        + "\n\nCandidate entry:\n" + input.candidate().describe()
                        + "\n\nSimilar entries I already remember:\n" + (existing.isEmpty() ? "(none)" : existing));
    }

    /** v0.0.28 🍊 Monitor text. */
    @Override
    protected String startText(Input input) {
        return "Comparing a new " + input.kind().label() + " with " + input.similar().size() + " similar entry(s)";
    }

    /** v0.0.28 🍊 Monitor text. */
    @Override
    protected String endText(MemoryJudgement output) {
        return switch (output.verdict()) {
            case NEW -> "Worth remembering";
            case DUPLICATE -> "Already known (" + output.action() + ")";
            case CONFLICT -> "Contradicts what I remember";
        };
    }

    /** v0.0.28 🍊 Ids must exist, MERGE needs text, CONFLICT needs a reason. */
    @Override
    protected List<String> semanticErrors(AgentContext ctx, Input input, MemoryJudgement output) {
        List<String> errors = new ArrayList<>();
        List<String> ids = input.similar().stream().map(StoredMemory::id).toList();
        boolean needsTarget = output.verdict() != MemoryJudgement.Verdict.NEW;
        if (needsTarget && (output.targetId() == null || !ids.contains(output.targetId()))) {
            errors.add("targetId must be one of the listed entry ids: " + String.join(", ", ids) + ".");
        }
        if (output.verdict() == MemoryJudgement.Verdict.DUPLICATE
                && output.action() == MemoryJudgement.Action.MERGE
                && (output.mergedText() == null || output.mergedText().isBlank())) {
            errors.add("action is MERGE but mergedText is empty.");
        }
        if (output.verdict() == MemoryJudgement.Verdict.CONFLICT
                && (output.conflictReason() == null || output.conflictReason().isBlank())) {
            errors.add("verdict is CONFLICT but conflictReason is empty.");
        }
        return errors;
    }

    /**
     * v0.0.28 🍊 When the judge is unavailable, keep the candidate as a new entry.
     *
     * <p>Losing something the agent decided was worth remembering is worse than a near-duplicate, and NEW can
     * never overwrite or discard an existing memory. Exact repeats are still stopped by the content hash.</p>
     */
    @Override
    protected Optional<MemoryJudgement> degrade(AgentContext ctx, Input input, YuzuException error) {
        return Optional.of(new MemoryJudgement("memory judge unavailable (" + error.code() + "); keeping it as a new entry",
                MemoryJudgement.Verdict.NEW, null, MemoryJudgement.Action.IGNORE, null, null));
    }
}
