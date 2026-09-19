package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;

import java.util.Map;
import java.util.Optional;

/** v0.0.12 🍊 The monitor's view of the agent registry: where an agent sits and whether it is paused or retired. */
public interface AgentLookup {

    /** v0.0.12 🍊 Placement of an agent, empty when the id is unknown. */
    Optional<Placement> find(AgentId agentId);

    /** v0.0.12 🍊 Every present (non-retired) agent with its placement, read once at startup. */
    default Map<AgentId, Placement> presentAgents() {
        return Map.of();
    }

    /** v0.0.12 🍊 Room of the agent plus its pause and retirement flags. */
    record Placement(String roomId, boolean paused, boolean retired) {
    }
}
