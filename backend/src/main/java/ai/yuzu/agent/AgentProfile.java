package ai.yuzu.agent;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;

import java.time.Instant;

/**
 * v0.0.6 🍊 Immutable personal profile of an agent: identity, job, persona and permission scope.
 *
 * <p>Every module prompt renders this profile; the scope is what code-level guards check.</p>
 */
public record AgentProfile(AgentId agentId, String roomId, String name, String avatarKey, String color, Role role,
                           String title, String scopeText, String persona, PermissionScope scope, State state,
                           int version, Instant createdAt, Instant updatedAt) {

    /** v0.0.6 🍊 Lifecycle state of an agent. */
    public enum State { ACTIVE, PAUSED, RETIRED }

    /** v0.0.6 🍊 True when the agent can receive work (not retired). */
    public boolean isPresent() {
        return state != State.RETIRED;
    }

    /** v0.0.6 🍊 English profile block rendered into prompts (stable text for caching). */
    public String describe() {
        return "Name: " + name + " (" + agentId + ")\n"
                + "Title: " + title + " [" + role.name() + "]\n"
                + "Work scope: " + scopeText + "\n"
                + "Persona: " + persona + "\n"
                + "Permissions and limits:\n" + scope.describe();
    }

    /** v0.0.6 🍊 API view (contract type {@code Agent}). */
    public AgentView toView(NaturalTime time) {
        return new AgentView(agentId.value(), roomId, name, avatarKey, color, role, title, scopeText, persona,
                scope.names(), scope.limits(), state, time.compact(createdAt));
    }
}
