package ai.yuzu.agent;

import jakarta.validation.constraints.Size;

import java.util.List;

/** v0.0.6 🍊 Body of {@code PATCH /api/agents/{agentId}}; null fields stay unchanged. */
public record UpdateAgentRequest(@Size(max = 80) String title,
                                 @Size(max = 2000) String scopeText,
                                 @Size(max = 2000) String persona,
                                 List<Permission> permissions,
                                 Limits.LimitsPatch limits) {
}
