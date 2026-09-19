package ai.yuzu.workspace;

import ai.yuzu.common.id.AgentId;

/** v0.0.11 🍊 Supplies an agent's workspace quota; read on every metered write so limit edits apply at once. */
@FunctionalInterface
public interface WorkspaceQuota {

    /** v0.0.11 🍊 The maximum number of metered bytes the agent may store. */
    long quotaBytes(AgentId agentId);
}
