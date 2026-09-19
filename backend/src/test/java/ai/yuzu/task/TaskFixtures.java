package ai.yuzu.task;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Permission;
import ai.yuzu.agent.Role;
import ai.yuzu.room.HumanUserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** v0.0.20 🍊 Creates isolated rooms, humans and agents for task-system integration tests. */
public final class TaskFixtures {

    private final JdbcClient jdbc;
    private final AgentService agents;
    private final HumanUserService humans;

    /** v0.0.20 🍊 Wraps the beans the fixtures need. */
    public TaskFixtures(JdbcClient jdbc, AgentService agents, HumanUserService humans) {
        this.jdbc = jdbc;
        this.agents = agents;
        this.humans = humans;
    }

    /** v0.0.20 🍊 A fresh room {@code room-fxxx} (the f-range stays clear of rooms other tests insert by hand). */
    public String newRoom() {
        for (int attempt = 0; attempt < 100; attempt++) {
            String roomId = String.format(Locale.ROOT, "room-f%03x", ThreadLocalRandom.current().nextInt(0x1000));
            try {
                jdbc.sql("INSERT INTO room (id, name, created_at) VALUES (:id, :name, UTC_TIMESTAMP(3))")
                        .param("id", roomId).param("name", "Tasks " + roomId).update();
                return roomId;
            } catch (DuplicateKeyException e) {
                // Used by an earlier test of this run: pick another id.
            }
        }
        throw new IllegalStateException("No free test room id left.");
    }

    /** v0.0.20 🍊 Hires an agent with the role's default permissions. */
    public AgentProfile hire(String roomId, Role role) {
        return agents.create(roomId, new CreateAgentRequest(role, null, null, null, null, null));
    }

    /** v0.0.20 🍊 Hires an agent with an explicit permission set. */
    public AgentProfile hire(String roomId, Role role, Permission... permissions) {
        return agents.create(roomId, new CreateAgentRequest(role, null, null, null, List.of(permissions), null));
    }

    /** v0.0.20 🍊 Joins a human to the room and returns the actor. */
    public Actor human(String roomId, String username) {
        return Actor.of(humans.join(roomId, username));
    }
}
