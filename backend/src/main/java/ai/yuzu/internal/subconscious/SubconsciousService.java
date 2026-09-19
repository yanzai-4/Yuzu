package ai.yuzu.internal.subconscious;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntime;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.internal.consciousness.Origin;
import ai.yuzu.internal.consciousness.PoolMessage;
import ai.yuzu.internal.consciousness.PoolRenderer;
import ai.yuzu.internal.memory.LearningModule;
import ai.yuzu.internal.memory.MemoryModule;
import ai.yuzu.module.TaskStateProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * v0.0.28 🍊 Runs one subconscious pass (the {@link SubconsciousHandler}) over the messages of one round.
 *
 * <p>Order: settle and age the conflicts, drop the advice into the pool, then hand the learn and remember
 * candidates to the learning and memory modules. The advice enters as a SUBCONSCIOUS message, which by
 * construction is not a trigger: it joins whatever batch the main consciousness takes next, but never wakes it
 * on its own, and it never schedules another subconscious pass. Learning runs on its own virtual threads so a
 * slow judge does not hold the agent's two subconscious permits.</p>
 */
@Service
public class SubconsciousService implements SubconsciousHandler {

    private static final Logger log = LoggerFactory.getLogger(SubconsciousService.class);

    private final SubconsciousModule module;
    private final ConflictTracker conflicts;
    private final LearningModule learning;
    private final MemoryModule memory;
    private final AgentRuntimeManager runtimes;
    private final AsyncRunner runner;
    private final NaturalTime time;
    private final ObjectProvider<TaskStateProvider> tasks;

    /** v0.0.28 🍊 Injects collaborators (the task state provider is optional). */
    public SubconsciousService(SubconsciousModule module, ConflictTracker conflicts, LearningModule learning,
                               MemoryModule memory, AgentRuntimeManager runtimes, AsyncRunner runner,
                               NaturalTime time, ObjectProvider<TaskStateProvider> tasks) {
        this.module = module;
        this.conflicts = conflicts;
        this.learning = learning;
        this.memory = memory;
        this.runtimes = runtimes;
        this.runner = runner;
        this.time = time;
        this.tasks = tasks;
    }

    /** v0.0.28 🍊 One pass over this round's new messages; never throws (the subconscious must stay invisible). */
    @Override
    public void run(AgentId agentId, List<PoolMessage> newMessages) {
        AgentRuntime runtime = runtimes.find(agentId).orElse(null);
        if (runtime == null || newMessages.isEmpty()) {
            return;
        }
        String traceId = newMessages.stream().map(PoolMessage::traceId).filter(t -> t != null)
                .reduce((a, b) -> b).orElse(null);
        int depth = newMessages.stream().mapToInt(PoolMessage::causalDepth).max().orElse(0);
        AgentContext ctx = runtime.context(traceId, null, time);
        try {
            SubconsciousOutput output = module.run(ctx, new SubconsciousModule.Input(newMessages,
                    tasks.getIfAvailable(() -> TaskStateProvider.NONE).current(agentId),
                    conflicts.render(agentId)));
            conflicts.activate(ctx, output.conflictUpdates());
            if (output.advice() != null && !output.advice().isBlank()) {
                runtime.consciousness().offer(Origin.SUBCONSCIOUS, PoolRenderer.OWN_THOUGHT,
                        output.advice().strip(), ctx.traceId(), depth, false);
            }
            for (SubconsciousOutput.Habit habit : output.learn()) {
                runner.run("learning", agentId.value(),
                        () -> learning.learn(ctx, habit.name(), habit.scenario(), habit.technique()));
            }
            for (SubconsciousOutput.Fact fact : output.remember()) {
                runner.run("memory", agentId.value(),
                        () -> memory.remember(ctx, fact.title(), fact.content(), fact.keywords(), "SUBCONSCIOUS"));
            }
        } catch (RuntimeException e) {
            log.warn("Subconscious pass of {} failed silently: {}", agentId, e.toString());
            runner.report("subconscious", agentId.value(), e);
        }
    }
}
