package ai.yuzu.external.safety;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.prompt.SegmentRank;
import ai.yuzu.module.AiModule;
import ai.yuzu.module.ModuleDeps;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * v0.0.16 🍊 Shared logic of the two safety reviews (DEFAULT tier, cacheable: same content + same scope = same verdict).
 *
 * <p>Sees only the content, the security guideline (handbook) and the agent's permission scope. Fails closed.</p>
 */
public abstract class SafetyReviewModule extends AiModule<SafetyReviewModule.Input, SafetyVerdict> {

    /** v0.0.16 🍊 Marker put in violations when the review itself could not run. */
    public static final String UNAVAILABLE = "SAFETY_CHECK_UNAVAILABLE";

    /** v0.0.16 🍊 Content to review and where it came from. */
    public record Input(String content, String source) {
    }

    /** v0.0.16 🍊 Receives dependencies. */
    protected SafetyReviewModule(ModuleDeps deps) {
        super(deps);
    }

    /** v0.0.16 🍊 S3 = permission scope only, S7 = the content. */
    @Override
    protected void compose(AgentContext ctx, Input input, PromptBuilder prompt) {
        prompt.add(SegmentRank.S3_SELF, "Permission scope of the coworker",
                        ctx.profile().name() + " — " + ctx.profile().title() + "\n" + ctx.profile().scope().describe())
                .add(SegmentRank.S7_STIMULUS, "Content to review (source: " + input.source() + ")", input.content());
    }

    /** v0.0.16 🍊 UNSAFE needs violations; gate mode forbids masks; mask quotes must exist verbatim. */
    @Override
    protected List<String> semanticErrors(AgentContext ctx, Input input, SafetyVerdict output) {
        ArrayList<String> errors = new ArrayList<>();
        if (!output.safe() && output.violations().isEmpty()) {
            errors.add("verdict is UNSAFE but violations is empty: list the violations.");
        }
        for (SafetyVerdict.Mask mask : output.masks()) {
            if (mask.quote() == null || mask.quote().isEmpty() || !input.content().contains(mask.quote())) {
                errors.add("mask quote does not appear verbatim in the content: \"" + abbreviate(mask.quote()) + "\"");
            }
        }
        return errors;
    }

    /** v0.0.16 🍊 Fail closed when the review cannot run. */
    @Override
    protected Optional<SafetyVerdict> degrade(AgentContext ctx, Input input, YuzuException error) {
        return Optional.of(new SafetyVerdict("Safety review unavailable: " + error.code(), SafetyVerdict.Verdict.UNSAFE,
                List.of(UNAVAILABLE), List.of(), "The safety check is temporarily unavailable, so I could not process this."));
    }

    /** v0.0.16 🍊 Short form of a quote for feedback. */
    private static String abbreviate(String quote) {
        return quote == null ? "" : quote.length() > 60 ? quote.substring(0, 60) + "…" : quote;
    }
}
