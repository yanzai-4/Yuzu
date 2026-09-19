package ai.yuzu.tool.spi;

import ai.yuzu.agent.Permission;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.external.safety.SecurityIncidentService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * v0.0.18 🍊 Code-level permission checks (the LLM never decides permissions). Denials become security incidents.
 */
@Component
public class PermissionGuard {

    private final SecurityIncidentService incidents;

    /** v0.0.18 🍊 Injects the incident service. */
    public PermissionGuard(SecurityIncidentService incidents) {
        this.incidents = incidents;
    }

    /** v0.0.18 🍊 Throws PERMISSION_DENIED (and records an incident) unless the agent holds every permission. */
    public void require(ToolContext ctx, Permission... permissions) {
        for (Permission permission : permissions) {
            if (!ctx.profile().scope().has(permission)) {
                deny(ctx, ctx.profile().name() + " does not have the " + permission.name() + " permission.");
            }
        }
    }

    /** v0.0.18 🍊 Records a guard incident and throws PERMISSION_DENIED. */
    public void deny(ToolContext ctx, String reason) {
        incidents.record(ctx.agent().agentId(), SecurityIncidentService.Stage.GUARD, "DENIED", List.of(reason),
                ctx.instruction(), ctx.agent().traceId());
        throw new PermissionDeniedException(reason).forAgent(ctx.agent().agentId().value());
    }
}
