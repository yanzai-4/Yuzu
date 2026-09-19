package ai.yuzu.agent;

import java.util.List;

/** v0.0.6 🍊 API shape of an agent (contract type {@code Agent}). */
public record AgentView(String agentId, String roomId, String name, String avatarKey, String color, Role role,
                        String title, String scopeText, String persona, List<String> permissions, Limits limits,
                        AgentProfile.State state, String createdTime) {
}
