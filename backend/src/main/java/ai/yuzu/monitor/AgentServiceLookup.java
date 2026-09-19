package ai.yuzu.monitor;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentRepository;
import ai.yuzu.agent.AgentService;
import ai.yuzu.common.id.AgentId;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** v0.0.12 🍊 AgentLookup backed by the (Caffeine-cached) AgentService profiles and the agent registry table. */
@Component
class AgentServiceLookup implements AgentLookup {

    private final AgentService agents;
    private final AgentRepository repository;

    /** v0.0.12 🍊 Injects the agent service and repository. */
    AgentServiceLookup(AgentService agents, AgentRepository repository) {
        this.agents = agents;
        this.repository = repository;
    }

    /** v0.0.12 🍊 Room, pause and retirement flags from the agent's profile. */
    @Override
    public Optional<Placement> find(AgentId agentId) {
        return agents.find(agentId).map(AgentServiceLookup::placementOf);
    }

    /** v0.0.12 🍊 Every present agent of every room. */
    @Override
    public Map<AgentId, Placement> presentAgents() {
        Map<AgentId, Placement> present = new LinkedHashMap<>();
        for (AgentProfile profile : repository.findAllPresent()) {
            present.put(profile.agentId(), placementOf(profile));
        }
        return present;
    }

    /** v0.0.12 🍊 Placement derived from a profile. */
    private static Placement placementOf(AgentProfile profile) {
        return new Placement(profile.roomId(), profile.state() == AgentProfile.State.PAUSED, !profile.isPresent());
    }
}
