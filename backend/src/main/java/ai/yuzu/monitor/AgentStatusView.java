package ai.yuzu.monitor;

import java.util.List;

/**
 * v0.0.6 🍊 Live desk state of an agent (contract type {@code AgentStatus}); the bubble floats above its head.
 *
 * @param state          IDLE, WORKING, THINKING, TALKING, WAITING, PAUSED or ERROR
 * @param activeModules  modules currently running
 * @param bubble         module label + short summary of what the agent is doing
 * @param poolSize       messages waiting in the consciousness pool
 * @param pendingBatches action batches still being reviewed or executed
 */
public record AgentStatusView(String agentId, String state, List<String> activeModules, Bubble bubble, int poolSize,
                              int pendingBatches, String time) {

    /** v0.0.6 🍊 Text shown above the agent's head. */
    public record Bubble(String module, String summary) {
    }

    /** v0.0.6 🍊 Status of an agent that is not doing anything. */
    public static AgentStatusView idle(String agentId, boolean paused, String time) {
        return new AgentStatusView(agentId, paused ? "PAUSED" : "IDLE", List.of(),
                new Bubble(paused ? "Paused" : "Idle", paused ? "Taking a break." : "Waiting for something to do."),
                0, 0, time);
    }
}
