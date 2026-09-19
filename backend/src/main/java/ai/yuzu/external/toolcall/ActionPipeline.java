package ai.yuzu.external.toolcall;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntime;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.chat.ChatPost;
import ai.yuzu.chat.ChatService;
import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.external.behavior.BehaviorReviewModule;
import ai.yuzu.external.behavior.BehaviorVerdict;
import ai.yuzu.external.behavior.HighRiskReviewModule;
import ai.yuzu.external.behavior.HighRiskVerdict;
import ai.yuzu.external.safety.SafetyService;
import ai.yuzu.external.safety.SecurityIncidentService;
import ai.yuzu.internal.consciousness.ActionSubmitter;
import ai.yuzu.internal.consciousness.Origin;
import ai.yuzu.internal.intake.IntakePipeline;
import ai.yuzu.internal.intake.Stimulus;
import ai.yuzu.module.TaskStateProvider;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.ToolRegistry;
import ai.yuzu.tool.spi.ToolResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * v0.0.18 🍊 From the main consciousness's decision to results back in its pool.
 *
 * <p>Behavior review ∥ tool-call decomposition (confirmed optimization: nothing executes before the review
 * passes). Any non-compliant action rejects the whole batch: a REVIEW warning goes into the pool and the main
 * consciousness must re-request. High-risk calls get a second IMPORTANT-tier review. Then the dispatcher runs
 * every call in parallel; untrusted outputs go through the outbound safety review (only problematic passages
 * are masked); infeasible actions and errors are appended at the end; the results, stamped with the time
 * they finished, re-enter through the intake (planning ∥ cognition → pool). Three rejections in a row stop
 * the loop with a yellow notice.</p>
 */
@Service
public class ActionPipeline implements ActionSubmitter {

    static final int MAX_REJECTIONS = 3;

    private final BehaviorReviewModule behavior;
    private final ToolCallingModule toolCalling;
    private final HighRiskReviewModule highRisk;
    private final ToolDispatcher dispatcher;
    private final SafetyService safety;
    private final SecurityIncidentService incidents;
    private final IntakePipeline intake;
    private final AgentRuntimeManager runtimes;
    private final ActionBatchRepository batches;
    private final ChatService chat;
    private final AsyncRunner runner;
    private final NaturalTime time;
    private final ObjectProvider<TaskStateProvider> tasks;
    private final ToolRegistry registry;

    /** v0.0.18 🍊 Injects collaborators. */
    public ActionPipeline(BehaviorReviewModule behavior, ToolCallingModule toolCalling,
                          HighRiskReviewModule highRisk, ToolDispatcher dispatcher, SafetyService safety,
                          SecurityIncidentService incidents, IntakePipeline intake, AgentRuntimeManager runtimes,
                          ActionBatchRepository batches, ChatService chat, AsyncRunner runner, NaturalTime time,
                          ObjectProvider<TaskStateProvider> tasks, ToolRegistry registry) {
        this.behavior = behavior;
        this.toolCalling = toolCalling;
        this.highRisk = highRisk;
        this.dispatcher = dispatcher;
        this.safety = safety;
        this.incidents = incidents;
        this.intake = intake;
        this.runtimes = runtimes;
        this.batches = batches;
        this.chat = chat;
        this.runner = runner;
        this.time = time;
        this.tasks = tasks;
        this.registry = registry;
    }

    /** v0.0.18 🍊 Registers the batch and processes it asynchronously. */
    @Override
    public void submit(AgentContext ctx, String runId, List<String> actions, int causalDepth) {
        String batchId = IdGen.recordId(DataName.BATCH, ctx.agentId());
        batches.insert(ctx.agentId(), batchId, runId, actions, ctx.traceId(), time.nowInstant());
        ActionTracker tracker = runtimes.require(ctx.agentId()).component(ActionTracker.class);
        tracker.started(batchId, actions);
        runner.run("actions", ctx.agentId().value(), () -> {
            try {
                process(ctx, batchId, actions, causalDepth, tracker);
            } finally {
                tracker.finished(batchId);
            }
        });
    }

    /** v0.0.18 🍊 Actions in progress for the main consciousness's prompt. */
    @Override
    public String inProgress(AgentContext ctx) {
        return runtimes.find(ctx.agentId()).map(r -> r.component(ActionTracker.class).render()).orElse("(none)");
    }

    /** v0.0.18 🍊 Review ∥ plan → high-risk review → dispatch → mask → intake. */
    private void process(AgentContext ctx, String batchId, List<String> actions, int depth, ActionTracker tracker) {
        AgentRuntime runtime = runtimes.require(ctx.agentId());
        CompletableFuture<BehaviorVerdict> review = runner.supply("behavior", ctx.agentId().value(),
                () -> behavior.run(ctx, actions));
        CompletableFuture<ToolPlan> planned = runner.supply("tool-calling", ctx.agentId().value(),
                () -> toolCalling.run(ctx, actions));
        BehaviorVerdict verdict = review.join();
        if (!verdict.compliant()) {
            reject(ctx, runtime, batchId, tracker, verdict, verdict.warning(), depth,
                    verdict.violations().stream().map(v -> "action " + v.actionIndex() + ": " + v.reason()).toList());
            return;
        }
        ToolPlan plan = planned.join();
        List<ToolPlan.Call> risky = plan.calls().stream()
                .filter(c -> risk(c.tool()) == Risk.HIGH).toList();
        if (!risky.isEmpty()) {
            HighRiskVerdict hv = highRisk.run(ctx, new HighRiskReviewModule.Input(
                    BehaviorReviewModule.numbered(actions),
                    String.join("\n", risky.stream().map(c -> c.tool() + " " + c.argsJson()).toList()),
                    tasks.getIfAvailable(() -> TaskStateProvider.NONE).current(ctx.agentId())));
            if (!hv.approve() || hv.needsHuman()) {
                String warning = hv.approve()
                        ? "My second review says a human must confirm this risky step first (" + String.join("; ", hv.concerns())
                        + "). I should ask a human for approval before trying again."
                        : "My second review rejected the risky step: " + String.join("; ", hv.concerns())
                        + ". None of my planned actions ran; I must re-request without it.";
                incidents.record(ctx.agentId(), SecurityIncidentService.Stage.HIGH_RISK,
                        hv.approve() ? "NEEDS_HUMAN" : "REJECTED", hv.concerns(), String.join("\n", actions),
                        ctx.traceId());
                batches.status(ctx.agentId(), batchId, "REJECTED", hv, warning, time.nowInstant());
                runtime.consciousness().offer(Origin.REVIEW, "my behavior check", warning, ctx.traceId(), depth, false);
                return;
            }
        }
        tracker.approved();
        batches.status(ctx.agentId(), batchId, "DISPATCHING", verdict, null, time.nowInstant());
        List<ToolDispatcher.Outcome> outcomes = dispatcher.dispatch(ctx, batchId, actions, plan, depth);
        String text = render(ctx, actions, plan, outcomes);
        boolean waiting = outcomes.stream().anyMatch(o -> o.result().status() == ToolResult.Status.WAITING);
        batches.status(ctx.agentId(), batchId, waiting ? "WAITING" : "DONE", verdict, null, time.nowInstant());
        intake.deliver(ctx, new Stimulus.ToolResultsStimulus(batchId, text), text,
                "the results of my actions (finished at " + time.compact(time.nowInstant()) + ")", depth);
    }

    /** v0.0.18 🍊 Rejection path: incident, REVIEW warning into the pool; after 3 in a row a yellow notice instead. */
    private void reject(AgentContext ctx, AgentRuntime runtime, String batchId, ActionTracker tracker, Object review,
                        String warning, int depth, List<String> reasons) {
        incidents.record(ctx.agentId(), SecurityIncidentService.Stage.BEHAVIOR, "REJECTED", reasons,
                String.join("\n", reasons), ctx.traceId());
        batches.status(ctx.agentId(), batchId, "REJECTED", review, warning, time.nowInstant());
        if (tracker.rejected() >= MAX_REJECTIONS) {
            tracker.approved();
            chat.post(ChatPost.warning(runtime.profile().roomId(), ctx.agentId().value(), runtime.profile().name(),
                    "My behavior check rejected my plans " + MAX_REJECTIONS + " times in a row, so I stopped trying. "
                            + "A human may need to clarify the request.", ctx.traceId()));
            return;
        }
        String text = "My behavior check rejected my planned actions, so NONE of them ran: " + warning
                + "\nRejected: " + String.join("; ", reasons) + "\nI must re-request only the acceptable actions.";
        runtime.consciousness().offer(Origin.REVIEW, "my behavior check", text, ctx.traceId(), depth, false);
    }

    /** v0.0.18 🍊 Results text: one line per call with its finish time, masked when needed; infeasible actions last. */
    private String render(AgentContext ctx, List<String> actions, ToolPlan plan, List<ToolDispatcher.Outcome> outcomes) {
        StringBuilder sb = new StringBuilder("Results of my actions:\n");
        for (ToolDispatcher.Outcome o : outcomes) {
            ToolResult r = o.result();
            String output = r.output() == null ? "" : r.output();
            if (r.status() == ToolResult.Status.OK && o.tool() != null) {
                output = safety.mask(ctx, output, o.tool().spec().source(), o.tool().spec().trusted()).content();
            }
            sb.append("- [").append(o.call().actionIndex()).append("] ").append(actions.get(o.call().actionIndex()))
                    .append("\n  ").append(o.call().tool()).append(" → ").append(r.status()).append(" (finished at ")
                    .append(time.compact(r.completedAt())).append(")");
            if (o.tool() != null && r.status() == ToolResult.Status.OK) {
                sb.append(" — ").append(o.tool().spec().source());
            }
            sb.append(":\n  ").append(output.strip().replace("\n", "\n  ")).append('\n');
        }
        if (!plan.infeasible().isEmpty()) {
            sb.append("Could not do:\n");
            plan.infeasible().forEach(i -> sb.append("- [").append(i.actionIndex()).append("] ")
                    .append(actions.get(i.actionIndex())).append(" — ").append(i.reason()).append('\n'));
        }
        return sb.toString().strip();
    }

    /** v0.0.18 🍊 Risk of a tool by name (unknown tools are LOW; they fail at dispatch anyway). */
    private Risk risk(String name) {
        return registry.find(name).map(t -> t.spec().risk()).orElse(Risk.LOW);
    }
}
