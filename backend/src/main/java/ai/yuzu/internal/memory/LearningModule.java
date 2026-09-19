package ai.yuzu.internal.memory;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.realtime.ErrorReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * v0.0.28 🍊 The learning module (学习模块): the only writer of habit memory.
 *
 * <p>Two callers hand it work: the subconscious, when a round taught the agent how to work better, and the
 * {@code learn} tool, when the main consciousness says out loud that it wants to learn something. Both go
 * through exactly the same de-duplication and conflict flow ({@link MemoryWriter}), so a habit can never be
 * stored twice and a contradiction never silently overwrites what the agent already does.</p>
 */
@Service
public class LearningModule {

    private static final Logger log = LoggerFactory.getLogger(LearningModule.class);

    private final MemoryWriter writer;
    private final HabitMemoryService habits;
    private final ErrorReporter errors;

    /** v0.0.28 🍊 Injects collaborators. */
    public LearningModule(MemoryWriter writer, HabitMemoryService habits, ErrorReporter errors) {
        this.writer = writer;
        this.habits = habits;
        this.errors = errors;
    }

    /** v0.0.28 🍊 Offers one habit to habit memory; never throws (a failure becomes an IGNORED outcome). */
    public MemoryOutcome learn(AgentContext ctx, String name, String scenario, String technique) {
        MemoryCandidate candidate = MemoryCandidate.habit(name, scenario, technique);
        try {
            MemoryOutcome outcome = writer.write(ctx, habits, candidate);
            log.debug("{} learning \"{}\": {}", ctx.agentId(), candidate.title(), outcome.outcome());
            return outcome;
        } catch (YuzuException e) {
            errors.report("learning", ctx.agentId().value(), e);
            return MemoryOutcome.ignored("I could not store this habit right now (" + e.code() + ").");
        } catch (RuntimeException e) {
            errors.report("learning", ctx.agentId().value(), e);
            return MemoryOutcome.ignored("I could not store this habit right now.");
        }
    }
}
