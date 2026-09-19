package ai.yuzu.workspace;

import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.Limits;
import ai.yuzu.common.id.AgentId;
import org.springframework.stereotype.Component;

/** v0.0.11 🍊 Workspace quota taken from the agent's Limits.fileQuotaMb (MiB); unknown agents get the default limit. */
@Component
public class AgentWorkspaceQuota implements WorkspaceQuota {

    private static final long MIB = 1024L * 1024L;

    private final AgentService agents;

    /** v0.0.11 🍊 Injects the agent service (profiles are cached there, so this lookup is cheap). */
    public AgentWorkspaceQuota(AgentService agents) {
        this.agents = agents;
    }

    /** v0.0.11 🍊 fileQuotaMb × 1 MiB from the agent's current limits. */
    @Override
    public long quotaBytes(AgentId agentId) {
        int megabytes = agents.find(agentId).map(profile -> profile.scope().limits().fileQuotaMb())
                .orElseGet(() -> Limits.defaults().fileQuotaMb());
        return megabytes * MIB;
    }
}
