package ai.yuzu.external.toolcall;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.error.CancelledException;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.module.ModuleSpan;
import ai.yuzu.tool.spi.PermissionGuard;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolRegistry;
import ai.yuzu.tool.spi.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * v0.0.18 🍊 Executes a tool plan: every call in parallel on its own virtual thread, each with a monitor span,
 * argument validation, a code-level permission check, a timeout and a persisted {@code tool_call} row.
 *
 * <p>Failures never abort the batch: they become ERROR / DENIED / CANCELLED results the agent reads later.
 * Every result is stamped with the time it finished.</p>
 */
@Component
public class ToolDispatcher {

    /** v0.0.18 🍊 One finished call. */
    public record Outcome(ToolPlan.Call call, Tool<?> tool, String toolCallId, ToolResult result) {
    }

    private final ToolRegistry registry;
    private final PermissionGuard guard;
    private final ObjectMapper mapper;
    private final ToolCallRepository calls;
    private final AsyncRunner runner;
    private final NaturalTime time;
    private final ModuleDeps deps;

    /** v0.0.18 🍊 Injects collaborators. */
    public ToolDispatcher(ToolRegistry registry, PermissionGuard guard, ObjectMapper mapper, ToolCallRepository calls,
                          AsyncRunner runner, NaturalTime time, ModuleDeps deps) {
        this.registry = registry;
        this.guard = guard;
        this.mapper = mapper;
        this.calls = calls;
        this.runner = runner;
        this.time = time;
        this.deps = deps;
    }

    /** v0.0.18 🍊 Runs all calls in parallel and returns their outcomes ordered by action index. */
    public List<Outcome> dispatch(AgentContext ctx, String batchId, List<String> actions, ToolPlan plan, int depth) {
        List<CompletableFuture<Outcome>> futures = new ArrayList<>();
        for (ToolPlan.Call call : plan.calls()) {
            futures.add(runner.supply("tool:" + call.tool(), ctx.agentId().value(),
                    () -> execute(ctx, batchId, actions, call, depth)));
        }
        List<Outcome> outcomes = new ArrayList<>();
        for (CompletableFuture<Outcome> f : futures) {
            outcomes.add(f.join());
        }
        outcomes.sort(Comparator.comparingInt(o -> o.call().actionIndex()));
        return outcomes;
    }

    /** v0.0.18 🍊 Executes one call end to end (never throws). */
    private Outcome execute(AgentContext ctx, String batchId, List<String> actions, ToolPlan.Call call, int depth) {
        String toolCallId = IdGen.recordId(DataName.TOOL_CALL, ctx.agentId());
        String instruction = actions.get(call.actionIndex());
        ModuleSpan span = deps.reporter().start(ctx.agentId(), "TOOL", "Using " + call.tool(), ctx.traceId(),
                ctx.parentSpanId());
        calls.started(ctx.agentId(), toolCallId, batchId, call.actionIndex(), call.tool(), instruction, call.argsJson(),
                ctx.traceId(), time.nowInstant());
        Tool<?> tool = registry.find(call.tool()).orElse(null);
        ToolResult result;
        try {
            if (tool == null) {
                result = ToolResult.error("There is no tool named " + call.tool() + ".", time.nowInstant());
            } else {
                JsonNode args = mapper.readTree(call.argsJson());
                List<String> errors = registry.validateArgs(tool, args);
                if (!errors.isEmpty()) {
                    result = ToolResult.error("Invalid arguments: " + String.join("; ", errors), time.nowInstant());
                } else {
                    ToolContext tc = new ToolContext(ctx.withParent(span.spanId()), batchId, toolCallId,
                            call.actionIndex(), instruction, depth, span);
                    if (!ToolRegistry.permitted(tool, ctx.profile().scope())) {
                        guard.deny(tc, ctx.profile().name() + " is not allowed to use " + call.tool() + ".");
                    }
                    result = runWithTimeout(tool, tc, args);
                }
            }
        } catch (PermissionDeniedException e) {
            result = ToolResult.denied(e.getMessage(), time.nowInstant());
        } catch (CancelledException e) {
            result = new ToolResult(ToolResult.Status.CANCELLED, "Cancelled: " + e.getMessage(), time.nowInstant());
        } catch (YuzuException e) {
            result = ToolResult.error(e.getMessage(), time.nowInstant());
        } catch (Exception e) {
            result = ToolResult.error("The tool failed: " + e.getClass().getSimpleName(), time.nowInstant());
        }
        boolean trusted = tool != null && tool.spec().trusted();
        calls.finished(ctx.agentId(), toolCallId, result.status().name(), trusted, result.output(),
                result.status() == ToolResult.Status.OK || result.status() == ToolResult.Status.WAITING ? null
                        : result.output(), result.completedAt());
        if (result.status() == ToolResult.Status.OK || result.status() == ToolResult.Status.WAITING) {
            span.end(result.status() == ToolResult.Status.WAITING ? "Waiting for an answer" : "Done");
        } else {
            span.fail(new IllegalStateException(result.status() + ": " + result.output()));
        }
        return new Outcome(call, tool, toolCallId, result);
    }

    /** v0.0.18 🍊 Maps the arguments to the tool's record and runs it on a virtual thread with its timeout. */
    private <A> ToolResult runWithTimeout(Tool<A> tool, ToolContext tc, JsonNode args) throws Exception {
        A typed = mapper.treeToValue(args, tool.spec().argsType());
        AtomicReference<ToolResult> out = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = Thread.ofVirtual().name("tool-" + tool.spec().name()).start(() -> {
            try (var ignored = tc.agent().cancel().bindCurrentThread()) {
                out.set(tool.execute(tc, typed));
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        if (!worker.join(tool.spec().timeout())) {
            worker.interrupt();
            return ToolResult.error(tool.spec().name() + " timed out after " + tool.spec().timeout().toSeconds()
                    + " seconds.", time.nowInstant());
        }
        if (failure.get() instanceof Exception e) {
            throw e;
        }
        if (failure.get() instanceof Error e) {
            throw e;
        }
        return out.get() == null ? ToolResult.error("The tool returned nothing.", time.nowInstant()) : out.get();
    }
}
