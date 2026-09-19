package ai.yuzu.agent;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** v0.0.6 🍊 Body of {@code POST /api/rooms/{roomId}/agents}; omitted fields fall back to the role preset. */
public record CreateAgentRequest(@NotNull Role role,
                                 @Size(max = 80) String title,
                                 @Size(max = 2000) String scopeText,
                                 @Size(max = 2000) String persona,
                                 List<Permission> permissions,
                                 Limits.LimitsPatch limits) {
}
