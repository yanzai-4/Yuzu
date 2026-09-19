package ai.yuzu.task;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.room.UserView;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** v0.0.20 🍊 Who acts in the task system: a human user or an AI agent (kind, id, display name). */
public record Actor(Kind kind, String id, String name) {

    private static final int MAX_ID = 16;
    private static final int MAX_NAME = 64;

    /** v0.0.20 🍊 Actor kind, stored in the publisher_kind / creator_kind / assigned_by_kind columns. */
    public enum Kind {
        HUMAN,
        AGENT
    }

    /** v0.0.20 🍊 Validates the shape: agents carry an agent id, humans never do, ids and names fit their columns. */
    public Actor {
        if (kind == null) {
            throw new IllegalArgumentException("An actor needs a kind (HUMAN or AGENT).");
        }
        if (id == null || id.isBlank() || id.length() > MAX_ID) {
            throw new IllegalArgumentException("Invalid actor id: " + id);
        }
        if ((kind == Kind.AGENT) != AgentId.isValid(id)) {
            throw new IllegalArgumentException("Actor id " + id + " does not match the kind " + kind + ".");
        }
        name = name == null ? "" : name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Invalid actor name: '" + name + "'");
        }
    }

    /** v0.0.20 🍊 A human actor (user-xxxx and username). */
    public static Actor human(String userId, String username) {
        return new Actor(Kind.HUMAN, userId, username);
    }

    /** v0.0.20 🍊 An agent actor (agent-xxxx and citrus name). */
    public static Actor agent(AgentId agentId, String name) {
        return new Actor(Kind.AGENT, agentId.value(), name);
    }

    /** v0.0.20 🍊 The actor of a human user. */
    public static Actor of(UserView user) {
        return human(user.id(), user.username());
    }

    /** v0.0.20 🍊 The actor of an agent profile. */
    public static Actor of(AgentProfile profile) {
        return agent(profile.agentId(), profile.name());
    }

    /** v0.0.20 🍊 Rebuilds an actor from its kind / id / name columns; null when the id column is null. */
    public static Actor fromColumns(String kind, String id, String name) {
        return id == null ? null : new Actor(Kind.valueOf(kind), id, name);
    }

    /** v0.0.20 🍊 True for human users. */
    @JsonIgnore
    public boolean isHuman() {
        return kind == Kind.HUMAN;
    }

    /** v0.0.20 🍊 True for AI agents. */
    @JsonIgnore
    public boolean isAgent() {
        return kind == Kind.AGENT;
    }

    /** v0.0.20 🍊 The agent id of an agent actor; IllegalStateException for humans. */
    public AgentId agentId() {
        if (!isAgent()) {
            throw new IllegalStateException(name + " is a human, not an agent.");
        }
        return AgentId.of(id);
    }

    /** v0.0.20 🍊 True when both actors are the same person or agent (kind and id; names may differ). */
    public boolean sameAs(Actor other) {
        return other != null && kind == other.kind && id.equals(other.id);
    }

    /** v0.0.20 🍊 Label used in prompts and outcomes, such as "Alice (human)" or "Yuzu (agent)". */
    public String describe() {
        return name + (isHuman() ? " (human)" : " (agent)");
    }
}
