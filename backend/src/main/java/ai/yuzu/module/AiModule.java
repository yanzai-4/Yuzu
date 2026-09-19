package ai.yuzu.module;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.error.CancelledException;
import ai.yuzu.common.error.ErrorCode;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.llm.LlmCallContext;
import ai.yuzu.llm.prompt.Prompt;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.structured.OutputStrategy;
import ai.yuzu.llm.structured.SchemaInstructions;
import ai.yuzu.llm.structured.StructuredResult;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * v0.0.14 🍊 Template method shared by every AI module: monitor span → cache-ordered prompt → structured call →
 * semantic checks → graceful degradation → error surfacing.
 *
 * <p>Subclasses only describe WHAT they need ({@link #spec()}, {@link #compose}, {@link #semanticErrors},
 * {@link #degrade}); the base class guarantees the rest is identical everywhere: S0 handbook + S1 module
 * template (+ schema text when the provider cannot enforce it), the current time captured once per
 * invocation, tier-based gating and metering through the gateway, and a monitor span that always ends.
 * Modules are stateless singletons; all agent state arrives through {@link AgentContext} and the input.</p>
 */
public abstract class AiModule<I, O> {

    protected final ModuleDeps deps;

    /** v0.0.14 🍊 Receives the shared dependencies. */
    protected AiModule(ModuleDeps deps) {
        this.deps = deps;
    }

    /** v0.0.14 🍊 Static description (name, tier, template, output type). */
    protected abstract ModuleSpec<O> spec();

    /** v0.0.14 🍊 Adds the variable segments (S2..S7) in order. */
    protected abstract void compose(AgentContext ctx, I input, PromptBuilder prompt);

    /** v0.0.14 🍊 What the monitor shows while this module runs. */
    protected String startText(I input) {
        return spec().label();
    }

    /** v0.0.14 🍊 What the monitor shows when this module finishes. */
    protected String endText(O output) {
        return "done";
    }

    /** v0.0.14 🍊 Checks a schema cannot express; messages become retry feedback. */
    protected List<String> semanticErrors(AgentContext ctx, I input, O output) {
        return List.of();
    }

    /** v0.0.18 🍊 Extra static text appended to the module instructions in S1 (for example a tool catalog). */
    protected String staticInstructions() {
        return "";
    }

    /** v0.0.14 🍊 Fallback value when the model keeps failing (empty = rethrow). */
    protected Optional<O> degrade(AgentContext ctx, I input, YuzuException error) {
        return Optional.empty();
    }

    /** v0.0.14 🍊 Runs the module for an agent. */
    public final O run(AgentContext ctx, I input) {
        ModuleSpec<O> spec = spec();
        ModuleSpan span = deps.reporter().start(ctx.agentId(), spec.module(), startText(input), ctx.traceId(),
                ctx.parentSpanId());
        try {
            AgentContext child = ctx.withParent(span.spanId());
            Function<OutputStrategy, Prompt> promptFor = strategy -> {
                PromptBuilder builder = PromptBuilder.start(deps.prompts().handbook(), instructions(strategy));
                compose(child, input, builder);
                return builder.build(ctx.nowText());
            };
            StructuredResult<O> result = deps.gateway().structured(
                    new LlmCallContext(ctx.agentId(), spec.module(), spec.tier(), ctx.traceId(), ctx.cancel()),
                    spec.outputType(), promptFor, out -> semanticErrors(child, input, out), spec.cacheable());
            if (result.attempts() > 1) {
                span.detail("attempts", result.attempts());
            }
            span.end(endText(result.value()));
            return result.value();
        } catch (CancelledException e) {
            span.cancelled(e.getMessage());
            throw e;
        } catch (YuzuException e) {
            span.fail(e);
            Optional<O> fallback = degrade(ctx, input, e);
            deps.errors().report(spec.module().toLowerCase() + "-module", ctx.agentId().value(), e);
            return fallback.orElseThrow(() -> e);
        } catch (RuntimeException e) {
            span.fail(e);
            YuzuException wrapped = new YuzuException(ErrorCode.INTERNAL,
                    spec.label() + " failed unexpectedly: " + e.getClass().getSimpleName(), e)
                    .forAgent(ctx.agentId().value());
            Optional<O> fallback = degrade(ctx, input, wrapped);
            deps.errors().report(spec.module().toLowerCase() + "-module", ctx.agentId().value(), e);
            return fallback.orElseThrow(() -> wrapped);
        }
    }

    /** v0.0.18 🍊 Module template (+ static extras) plus schema instructions when the provider cannot enforce the schema. */
    private String instructions(OutputStrategy strategy) {
        String schema = SchemaInstructions.forStrategy(strategy, deps.schemas().schemaText(spec().outputType()));
        String template = deps.prompts().get("modules/" + spec().template());
        String extra = staticInstructions();
        String base = extra == null || extra.isBlank() ? template : template + "\n\n" + extra.strip();
        return schema.isEmpty() ? base : base + "\n\n" + schema;
    }
}
