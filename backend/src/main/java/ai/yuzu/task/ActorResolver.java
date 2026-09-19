package ai.yuzu.task;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.Permission;
import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.room.UserView;
import org.springframework.stereotype.Component;

/** v0.0.20 🍊 Resolves ids to verified actors and runs the code-level permission checks of the task system. */
@Component
public class ActorResolver {

    private final AgentService agents;
    private final HumanUserService humans;

    /** v0.0.20 🍊 Injects the agent and human directories. */
    public ActorResolver(AgentService agents, HumanUserService humans) {
        this.agents = agents;
        this.humans = humans;
    }

    /** v0.0.20 🍊 The actor of an existing human user (NOT_FOUND for unknown ids). */
    public Actor human(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("A user id is required.");
        }
        return Actor.of(humans.require(userId.strip()));
    }

    /** v0.0.20 🍊 The actor of an existing agent (NOT_FOUND for unknown ids). */
    public Actor agent(AgentId agentId) {
        return Actor.of(agents.require(agentId));
    }

    /** v0.0.20 🍊 Re-reads the actor, requires a present member of the room, and returns the canonical actor. */
    public Actor verify(Actor actor, String roomId) {
        if (actor == null) {
            throw new BadRequestException("An actor (a human or an agent) is required.");
        }
        if (actor.isHuman()) {
            UserView user = humans.require(actor.id());
            if (!user.roomId().equals(roomId)) {
                throw new PermissionDeniedException(user.username() + " is not a member of room " + roomId + ".")
                        .with("userId", user.id()).with("roomId", roomId);
            }
            return Actor.of(user);
        }
        AgentProfile profile = agents.require(actor.agentId());
        if (!profile.isPresent()) {
            throw new PermissionDeniedException(profile.name() + " is retired and can no longer act.")
                    .forAgent(profile.agentId().value());
        }
        if (!profile.roomId().equals(roomId)) {
            throw new PermissionDeniedException(profile.name() + " is not a member of room " + roomId + ".")
                    .with("roomId", roomId).forAgent(profile.agentId().value());
        }
        return Actor.of(profile);
    }

    /** v0.0.20 🍊 True when the actor may use the permission: humans always, agents only when their scope holds it. */
    public boolean allows(Actor actor, Permission permission) {
        if (actor.isHuman()) {
            return true;
        }
        return agents.find(actor.agentId())
                .filter(AgentProfile::isPresent)
                .map(profile -> profile.scope().has(permission))
                .orElse(false);
    }

    /** v0.0.20 🍊 Throws PERMISSION_DENIED unless the actor may use the permission for the described action. */
    public void require(Actor actor, Permission permission, String action) {
        if (!allows(actor, permission)) {
            throw new PermissionDeniedException(actor.name() + " may not " + action + " without the "
                    + permission.name() + " permission.")
                    .with("permission", permission.name()).forAgent(actor.id());
        }
    }
}
