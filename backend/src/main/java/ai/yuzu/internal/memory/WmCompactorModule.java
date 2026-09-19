package ai.yuzu.internal.memory;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.llm.ModelTier;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.prompt.SegmentRank;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.module.AiModule;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.module.ModuleSpec;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * v0.0.14 🍊 AI implementation of {@link WorkingMemoryCompactor} (DEFAULT tier): folds old entries into the digest.
 */
@Component
public class WmCompactorModule extends AiModule<WmCompactorModule.Input, WmCompactorModule.Digest>
        implements WorkingMemoryCompactor {

    /** v0.0.14 🍊 Input: the previous digest and the entries to fold in. */
    public record Input(String previousDigest, List<WorkingMemoryEntry> entries) {
    }

    /** v0.0.14 🍊 Output: the new digest. */
    public record Digest(@Desc("The new first-person summary, at most 250 words") String summary) {
    }

    private static final ModuleSpec<Digest> SPEC = new ModuleSpec<>("WM_COMPACTOR", "Tidying working memory",
            ModelTier.DEFAULT, "wm_compactor", Digest.class, false);

    private final AgentRuntimeManager runtimes;

    /** v0.0.14 🍊 Injects dependencies. */
    public WmCompactorModule(ModuleDeps deps, AgentRuntimeManager runtimes) {
        super(deps);
        this.runtimes = runtimes;
    }

    /** v0.0.14 🍊 Compactor entry point used by the working-memory service. */
    @Override
    public String compact(AgentId agentId, String previousDigest, List<WorkingMemoryEntry> entries) {
        AgentContext ctx = runtimes.require(agentId).context(null, null, deps.time());
        return run(ctx, new Input(previousDigest, entries)).summary();
    }

    /** v0.0.14 🍊 Static description. */
    @Override
    protected ModuleSpec<Digest> spec() {
        return SPEC;
    }

    /** v0.0.14 🍊 S5 = previous digest, S7 = entries to fold. */
    @Override
    protected void compose(AgentContext ctx, Input input, PromptBuilder prompt) {
        prompt.add(SegmentRank.S5_WORKING_MEMORY, "Previous summary",
                        input.previousDigest().isBlank() ? "(none yet)" : input.previousDigest())
                .add(SegmentRank.S7_STIMULUS, "Entries to fold into the summary", render(input.entries()));
    }

    /** v0.0.14 🍊 Monitor text. */
    @Override
    protected String startText(Input input) {
        return "Summarizing " + input.entries().size() + " old memories";
    }

    /** v0.0.14 🍊 Entries as lines with absolute times. */
    private String render(List<WorkingMemoryEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (WorkingMemoryEntry e : entries) {
            sb.append('[').append(deps.time().compact(e.createdAt())).append("] ")
                    .append(e.direction() == WorkingMemoryEntry.Direction.IN ? "IN from " : "OUT ")
                    .append(e.source()).append(": ").append(e.text().strip()).append('\n');
        }
        return sb.toString().strip();
    }
}
