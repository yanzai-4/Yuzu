package ai.yuzu.module;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.error.CancelledException;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.llm.LlmCallContext;
import ai.yuzu.llm.ModelTier;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.provider.LlmResult;
import ai.yuzu.llm.provider.StreamSink;

/**
 * v0.0.14 🍊 Template for free-text modules (for example composing a chat post), optionally streamed.
 */
public abstract class TextAiModule<I> {

    protected final ModuleDeps deps;

    /** v0.0.14 🍊 Receives the shared dependencies. */
    protected TextAiModule(ModuleDeps deps) {
        this.deps = deps;
    }

    /** v0.0.14 🍊 Module name (monitor, gating, metering). */
    protected abstract String module();

    /** v0.0.14 🍊 Model tier. */
    protected abstract ModelTier tier();

    /** v0.0.14 🍊 Prompt template name under prompts/modules/. */
    protected abstract String template();

    /** v0.0.14 🍊 Adds the variable segments (S2..S7). */
    protected abstract void compose(AgentContext ctx, I input, PromptBuilder prompt);

    /** v0.0.14 🍊 Monitor text while running. */
    protected String startText(I input) {
        return module();
    }

    /** v0.0.14 🍊 Generates the text, streaming deltas to the sink when it is not null. */
    public final String generate(AgentContext ctx, I input, StreamSink sink) {
        ModuleSpan span = deps.reporter().start(ctx.agentId(), module(), startText(input), ctx.traceId(),
                ctx.parentSpanId());
        try {
            PromptBuilder builder = PromptBuilder.start(deps.prompts().handbook(),
                    deps.prompts().get("modules/" + template()));
            compose(ctx.withParent(span.spanId()), input, builder);
            LlmResult result = deps.gateway().text(
                    new LlmCallContext(ctx.agentId(), module(), tier(), ctx.traceId(), ctx.cancel()),
                    builder.build(ctx.nowText()), sink);
            span.end("wrote " + result.text().length() + " characters");
            return result.text().strip();
        } catch (CancelledException e) {
            span.cancelled(e.getMessage());
            throw e;
        } catch (YuzuException e) {
            span.fail(e);
            deps.errors().report(module().toLowerCase() + "-module", ctx.agentId().value(), e);
            throw e;
        }
    }
}
