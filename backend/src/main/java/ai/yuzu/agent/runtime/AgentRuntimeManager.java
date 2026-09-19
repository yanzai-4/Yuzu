package ai.yuzu.agent.runtime;

import ai.yuzu.agent.AgentLifecycleListener;
import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentRepository;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.internal.consciousness.ConsciousnessFactory;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v0.0.6 🍊 Registry of live {@link AgentRuntime}s: created at boot and on hire, updated on edits, removed on retire.
 */
@Component
public class AgentRuntimeManager implements AgentLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeManager.class);

    private final AgentRepository repository;
    private final ConsciousnessFactory consciousness;
    private final ObjectProvider<AgentComponentFactory<?>> componentFactories;
    private final Map<AgentId, AgentRuntime> runtimes = new ConcurrentHashMap<>();

    /** v0.0.15 🍊 Injects the repository (boot), the consciousness factory and the per-agent component factories. */
    public AgentRuntimeManager(AgentRepository repository, ConsciousnessFactory consciousness,
                               ObjectProvider<AgentComponentFactory<?>> componentFactories) {
        this.repository = repository;
        this.consciousness = consciousness;
        this.componentFactories = componentFactories;
    }

    /** v0.0.6 🍊 Starts a runtime for every present agent once the application is ready. */
    @EventListener(ApplicationReadyEvent.class)
    public void startAll() {
        repository.findAllPresent().forEach(p -> runtimes.computeIfAbsent(p.agentId(), id -> newRuntime(p)));
        log.info("🍊 Started {} agent runtimes", runtimes.size());
    }

    /** v0.0.6 🍊 Runtime of an agent, if it is alive. */
    public Optional<AgentRuntime> find(AgentId agentId) {
        return Optional.ofNullable(runtimes.get(agentId));
    }

    /** v0.0.6 🍊 Runtime of an agent or NOT_FOUND. */
    public AgentRuntime require(AgentId agentId) {
        return find(agentId).orElseThrow(() -> new NotFoundException("Agent " + agentId + " is not running.")
                .forAgent(agentId.value()));
    }

    /** v0.0.6 🍊 Every live runtime. */
    public Collection<AgentRuntime> all() {
        return runtimes.values();
    }

    /** v0.0.6 🍊 Creates the runtime of a newly hired agent. */
    @Override
    public void onCreated(AgentProfile profile) {
        runtimes.computeIfAbsent(profile.agentId(), id -> newRuntime(profile));
    }

    /** v0.0.6 🍊 Pushes the edited profile (and pause state) into the runtime. */
    @Override
    public void onUpdated(AgentProfile profile) {
        AgentRuntime runtime = runtimes.computeIfAbsent(profile.agentId(), id -> newRuntime(profile));
        runtime.updateProfile(profile);
        if (profile.state() == AgentProfile.State.PAUSED) {
            runtime.interrupt("paused");
        }
    }

    /** v0.0.6 🍊 Stops and forgets the runtime of a retired agent. */
    @Override
    public void onRetired(AgentProfile profile) {
        AgentRuntime runtime = runtimes.remove(profile.agentId());
        if (runtime != null) {
            runtime.shutdown("retired");
        }
    }

    /** v0.0.15 🍊 Builds a runtime with its own consciousness and components. */
    private AgentRuntime newRuntime(AgentProfile profile) {
        AgentRuntime runtime = new AgentRuntime(profile,
                consciousness.create(profile.agentId(), profile.state() == AgentProfile.State.PAUSED));
        componentFactories.orderedStream().forEach(f -> runtime.register(f.type(), f.create(profile)));
        return runtime;
    }

    /** v0.0.6 🍊 Cancels all in-flight agent work on shutdown. */
    @PreDestroy
    public void shutdownAll() {
        runtimes.values().forEach(r -> r.shutdown("server shutdown"));
    }
}
