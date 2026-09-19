package ai.yuzu.tool.impl.memory;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.ModelTier;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.prompt.SegmentRank;
import ai.yuzu.module.AiModule;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.module.ModuleSpec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * v0.0.19 🍊 The memory-read module (tool layer, DEFAULT tier): turns a recall request whose time phrase code could
 * not resolve into keywords and an absolute range, given the current time (always the last prompt segment).
 */
@Component
public class MemoryReadModule extends AiModule<MemoryReadModule.Input, RecallPlan> {

    /** v0.0.19 🍊 What to recall and the (optional) time phrase code could not parse. */
    public record Input(String query, String timePhrase) {
    }

    private static final ModuleSpec<RecallPlan> SPEC = new ModuleSpec<>("MEMORY_READ", "Recalling",
            ModelTier.DEFAULT, "memory_read", RecallPlan.class, false);

    private final NaturalTime time;

    /** v0.0.19 🍊 Receives dependencies. */
    public MemoryReadModule(ModuleDeps deps, NaturalTime time) {
        super(deps);
        this.time = time;
    }

    /** v0.0.19 🍊 Static description. */
    @Override
    protected ModuleSpec<RecallPlan> spec() {
        return SPEC;
    }

    /** v0.0.19 🍊 S7: the request (the current time follows in S8). */
    @Override
    protected void compose(AgentContext ctx, Input input, PromptBuilder prompt) {
        prompt.add(SegmentRank.S7_STIMULUS, "What to recall", input.query()
                + (input.timePhrase() == null ? "" : "\nTime expression: " + input.timePhrase()));
    }

    /** v0.0.19 🍊 Times must parse, come in pairs, be ordered and not lie in the future. */
    @Override
    protected List<String> semanticErrors(AgentContext ctx, Input input, RecallPlan output) {
        List<String> errors = new ArrayList<>();
        if ((output.fromTime() == null) != (output.toTime() == null)) {
            errors.add("fromTime and toTime must both be null or both be set.");
            return errors;
        }
        if (output.fromTime() == null) {
            return errors;
        }
        Optional<Instant> from = time.parseMachine(output.fromTime());
        Optional<Instant> to = time.parseMachine(output.toTime());
        if (from.isEmpty()) {
            errors.add("fromTime \"" + output.fromTime() + "\" is not in the format yyyy-MM-dd HH:mm:ss.");
        }
        if (to.isEmpty()) {
            errors.add("toTime \"" + output.toTime() + "\" is not in the format yyyy-MM-dd HH:mm:ss.");
        }
        if (from.isPresent() && to.isPresent()) {
            if (from.get().isAfter(to.get())) {
                errors.add("fromTime is after toTime.");
            }
            if (from.get().isAfter(ctx.now().plus(Duration.ofMinutes(1)))) {
                errors.add("fromTime lies in the future; memories can only be in the past.");
            }
        }
        return errors;
    }

    /** v0.0.19 🍊 Degrades to a plain keyword recall without a time range. */
    @Override
    protected Optional<RecallPlan> degrade(AgentContext ctx, Input input, YuzuException error) {
        return Optional.of(new RecallPlan("memory-read module unavailable", List.of(), null, null));
    }

    /** v0.0.19 🍊 Monitor text. */
    @Override
    protected String endText(RecallPlan output) {
        return output.fromTime() == null ? "Searching by keywords" : "Searching " + output.fromTime() + " – " + output.toTime();
    }
}
