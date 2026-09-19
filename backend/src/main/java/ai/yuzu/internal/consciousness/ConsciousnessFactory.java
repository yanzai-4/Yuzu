package ai.yuzu.internal.consciousness;

import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.internal.subconscious.SubconsciousHandler;
import ai.yuzu.internal.subconscious.SubconsciousScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * v0.0.12 🍊 Builds the per-agent {@link Consciousness}. Module handlers are resolved lazily at run time, so the
 * main/subconscious modules can depend on the runtime registry without construction cycles.
 */
@Component
public class ConsciousnessFactory {

    private static final Logger log = LoggerFactory.getLogger(ConsciousnessFactory.class);

    private final PoolMessageRepository repository;
    private final AsyncRunner runner;
    private final NaturalTime time;
    private final ObjectProvider<MainRunHandler> mainHandlers;
    private final ObjectProvider<SubconsciousHandler> subconsciousHandlers;

    /** v0.0.12 🍊 Injects collaborators. */
    public ConsciousnessFactory(PoolMessageRepository repository, AsyncRunner runner, NaturalTime time,
                                ObjectProvider<MainRunHandler> mainHandlers,
                                ObjectProvider<SubconsciousHandler> subconsciousHandlers) {
        this.repository = repository;
        this.runner = runner;
        this.time = time;
        this.mainHandlers = mainHandlers;
        this.subconsciousHandlers = subconsciousHandlers;
    }

    /** v0.0.12 🍊 Creates the consciousness of one agent. */
    public Consciousness create(AgentId agentId, boolean paused) {
        ConsciousnessPool pool = new ConsciousnessPool();
        SubconsciousScheduler subconscious = new SubconsciousScheduler(agentId, (agent, messages) -> {
            SubconsciousHandler handler = subconsciousHandlers.getIfAvailable();
            if (handler != null) {
                handler.run(agent, messages);
            }
        }, runner);
        MainLoop loop = new MainLoop(agentId, pool, (agent, batch) -> {
            MainRunHandler handler = mainHandlers.getIfAvailable();
            if (handler == null) {
                log.warn("No main consciousness handler registered; dropping {} pool messages of {}", batch.size(),
                        agent);
                return;
            }
            handler.runOnce(agent, batch);
        }, runner, subconscious::onNew);
        if (paused) {
            pool.setPaused(true);
        }
        return new Consciousness(agentId, pool, loop, subconscious, repository, time);
    }
}
