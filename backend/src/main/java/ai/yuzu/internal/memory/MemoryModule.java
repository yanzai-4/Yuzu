package ai.yuzu.internal.memory;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.realtime.ErrorReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * v0.0.28 🍊 The memory module (记忆模块): the only writer of deep memory.
 *
 * <p>The subconscious hands it facts worth keeping for a long time. The flow is the one the learning module
 * uses, with the {@code UNIQUE(agent_id, content_hash)} key doing the cheap work: the same fact noticed in two
 * rounds costs no model call at all. What it writes is readable immediately by the memory-read tool, which
 * queries {@code deep_memory} directly.</p>
 */
@Service
public class MemoryModule {

    private static final Logger log = LoggerFactory.getLogger(MemoryModule.class);

    private final MemoryWriter writer;
    private final DeepMemoryService deep;
    private final ErrorReporter errors;

    /** v0.0.28 🍊 Injects collaborators. */
    public MemoryModule(MemoryWriter writer, DeepMemoryService deep, ErrorReporter errors) {
        this.writer = writer;
        this.deep = deep;
        this.errors = errors;
    }

    /** v0.0.28 🍊 Offers one fact to deep memory; never throws (a failure becomes an IGNORED outcome). */
    public MemoryOutcome remember(AgentContext ctx, String title, String content, List<String> keywords,
                                  String source) {
        MemoryCandidate candidate = MemoryCandidate.deep(title, content, keywords, source);
        try {
            MemoryOutcome outcome = writer.write(ctx, deep, candidate);
            log.debug("{} remembering \"{}\": {}", ctx.agentId(), candidate.title(), outcome.outcome());
            return outcome;
        } catch (YuzuException e) {
            errors.report("memory", ctx.agentId().value(), e);
            return MemoryOutcome.ignored("I could not store this memory right now (" + e.code() + ").");
        } catch (RuntimeException e) {
            errors.report("memory", ctx.agentId().value(), e);
            return MemoryOutcome.ignored("I could not store this memory right now.");
        }
    }
}
