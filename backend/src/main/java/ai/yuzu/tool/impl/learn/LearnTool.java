package ai.yuzu.tool.impl.learn;

import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.internal.memory.LearningModule;
import ai.yuzu.internal.memory.MemoryOutcome;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.tool.spi.PermissionGuard;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.tool.spi.ToolSpec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * v0.0.28 🍊 "I need to learn this": hands a habit from the main consciousness to the learning module.
 *
 * <p>The subconscious learns on its own, quietly. This tool is the other door into habit memory: when the main
 * consciousness itself concludes that it should work differently from now on, it says so as an action and the
 * learning module applies exactly the same flow — content hash, FULLTEXT search for what the agent already
 * does, the judge, and a contradiction held for ten rounds instead of an overwrite.</p>
 *
 * <p>No permission gates it: remembering how one's own work went is not an outside effect, and an agent that
 * could act but not learn from it would repeat every mistake. The guard is still called first, so the
 * defense-in-depth contract holds if a permission is ever added. The result is written by our own code, so it
 * is trusted and skips the AI outbound review.</p>
 */
@Component
public class LearnTool implements Tool<LearnTool.Args> {

    /** v0.0.28 🍊 Arguments. */
    public record Args(@Desc("Short name of the habit, for example 'Cite every source'") String name,
                       @Desc("When it applies, starting with 'when ...'") String scenario,
                       @Desc("Exactly what to do, concrete enough to follow later without thinking it through again") String technique) {
    }

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("learn",
            "Learn a way of working for good: give it a name, when it applies and exactly what to do. Use it when "
                    + "I conclude that I should handle this kind of situation differently from now on. It is kept in "
                    + "my habit memory and recalled automatically; it is not for one-off facts.",
            Args.class, Set.of(), Risk.LOW, Duration.ofSeconds(90), true, "I noted this down for myself");

    private final LearningModule learning;
    private final PermissionGuard guard;
    private final NaturalTime time;

    /** v0.0.28 🍊 Injects collaborators. */
    public LearnTool(LearningModule learning, PermissionGuard guard, NaturalTime time) {
        this.learning = learning;
        this.guard = guard;
        this.time = time;
    }

    /** v0.0.28 🍊 Static description. */
    @Override
    public ToolSpec<Args> spec() {
        return SPEC;
    }

    /** v0.0.28 🍊 Offers the habit to the learning module and reports what it did with it. */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, SPEC.permissions().toArray(ai.yuzu.agent.Permission[]::new));
        if (args.name() == null || args.name().isBlank() || args.technique() == null || args.technique().isBlank()) {
            return ToolResult.error("A habit needs both a name and a technique.", time.nowInstant());
        }
        MemoryOutcome outcome = learning.learn(ctx.agent().withParent(ctx.span().spanId()), args.name(),
                args.scenario(), args.technique());
        return outcome.outcome() == MemoryOutcome.Outcome.IGNORED
                ? ToolResult.error(outcome.note(), time.nowInstant())
                : ToolResult.ok(outcome.note(), time.nowInstant());
    }
}
