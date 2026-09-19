package ai.yuzu.internal.consciousness;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntime;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.internal.memory.WorkingMemoryService;
import ai.yuzu.module.TaskStateProvider;
import ai.yuzu.module.ToolCatalogProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * v0.0.17 🍊 Runs one main-consciousness step for a drained pool batch (the {@link MainRunHandler}).
 *
 * <p>Order: record inputs in working memory (own THINK messages are skipped: already stored as outputs) →
 * main module → record the output → persist the run → act on the mode. THINK appends a SELF message (a
 * trigger, so the loop continues immediately); after {@value #MAX_THINK_STREAK} consecutive THINKs code forces
 * END. ACT hands the actions to the action pipeline asynchronously, so the loop is free for new input.</p>
 */
@Service
public class MainConsciousnessService implements MainRunHandler {

    /** v0.0.17 🍊 Consecutive THINK steps after which code forces END. */
    static final int MAX_THINK_STREAK = 8;

    private static final Logger log = LoggerFactory.getLogger(MainConsciousnessService.class);

    private final MainConsciousnessModule module;
    private final AgentRuntimeManager runtimes;
    private final WorkingMemoryService workingMemory;
    private final MainRunRepository runs;
    private final NaturalTime time;
    private final ObjectProvider<TaskStateProvider> tasks;
    private final ObjectProvider<ToolCatalogProvider> tools;
    private final ObjectProvider<ActionSubmitter> actions;

    /** v0.0.17 🍊 Injects collaborators (task state, tool catalog and action submitter are optional). */
    public MainConsciousnessService(MainConsciousnessModule module, AgentRuntimeManager runtimes,
                                    WorkingMemoryService workingMemory, MainRunRepository runs, NaturalTime time,
                                    ObjectProvider<TaskStateProvider> tasks, ObjectProvider<ToolCatalogProvider> tools,
                                    ObjectProvider<ActionSubmitter> actions) {
        this.module = module;
        this.runtimes = runtimes;
        this.workingMemory = workingMemory;
        this.runs = runs;
        this.time = time;
        this.tasks = tasks;
        this.tools = tools;
        this.actions = actions;
    }

    /** v0.0.17 🍊 One step over a batch. */
    @Override
    public void runOnce(AgentId agentId, List<PoolMessage> batch) {
        AgentRuntime runtime = runtimes.require(agentId);
        String traceId = batch.stream().map(PoolMessage::traceId).filter(t -> t != null).reduce((a, b) -> b)
                .orElse(null);
        int depth = batch.stream().mapToInt(PoolMessage::causalDepth).max().orElse(0);
        AgentContext ctx = runtime.context(traceId, null, time);
        Instant started = ctx.now();
        String runId = IdGen.recordId(DataName.RUN, agentId);
        MindState mind = runtime.component(MindState.class);
        ActionSubmitter submitter = actions.getIfAvailable();

        workingMemory.recordInputs(agentId, runId, batch);
        MainDecision decision = module.run(ctx, new MainConsciousnessModule.Input(batch,
                tasks.getIfAvailable(() -> TaskStateProvider.NONE).current(agentId),
                tools.getIfAvailable(() -> ToolCatalogProvider.NONE).permittedTools(runtime.profile().scope()),
                submitter == null ? "(none)" : submitter.inProgress(ctx), mind.streak()));

        MainDecision.Mode mode = decision.mode();
        if (mode == MainDecision.Mode.THINK && mind.streak() + 1 >= MAX_THINK_STREAK) {
            log.info("{} thought {} times in a row; forcing END", agentId, MAX_THINK_STREAK);
            mode = MainDecision.Mode.END;
        }
        workingMemory.recordOutput(agentId, runId, render(decision, mode));
        runs.insert(agentId, runId, mode.name(), decision.thought(), decision.actions(), decision.nextThought(),
                batch.stream().map(PoolMessage::id).toList(), ctx.traceId(), started, time.nowInstant());
        runtime.consciousness().markConsumed(batch, runId);

        switch (mode) {
            case THINK -> {
                mind.thought();
                runtime.consciousness().offer(Origin.SELF, PoolRenderer.OWN_THOUGHT, decision.nextThought(),
                        ctx.traceId(), depth, true);
            }
            case ACT -> {
                mind.decided();
                if (submitter == null) {
                    log.warn("No action pipeline registered; {} cannot act on {}", agentId, decision.actions());
                } else {
                    submitter.submit(ctx, runId, decision.actions(), depth);
                }
            }
            case END -> mind.decided();
        }
    }

    /** v0.0.17 🍊 Working-memory text of the run's output. */
    static String render(MainDecision decision, MainDecision.Mode mode) {
        StringBuilder sb = new StringBuilder("I thought: ").append(decision.thought().strip()).append("\nI decided: ")
                .append(mode.name());
        if (mode == MainDecision.Mode.ACT) {
            for (int i = 0; i < decision.actions().size(); i++) {
                sb.append("\n").append(i + 1).append(". ").append(decision.actions().get(i).strip());
            }
        } else if (mode == MainDecision.Mode.THINK) {
            sb.append(" — next: ").append(decision.nextThought().strip());
        }
        return sb.toString();
    }
}
