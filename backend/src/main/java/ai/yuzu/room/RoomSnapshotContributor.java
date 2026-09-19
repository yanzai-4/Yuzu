package ai.yuzu.room;

import ai.yuzu.bootstrap.SnapshotBuilder;
import ai.yuzu.bootstrap.SnapshotContributor;
import org.springframework.stereotype.Component;

/** v0.0.5 🍊 Adds the room name and its human users to the bootstrap snapshot. */
@Component
public class RoomSnapshotContributor implements SnapshotContributor {

    private final RoomRepository rooms;
    private final HumanUserService users;

    /** v0.0.5 🍊 Injects collaborators. */
    public RoomSnapshotContributor(RoomRepository rooms, HumanUserService users) {
        this.rooms = rooms;
        this.users = users;
    }

    /** v0.0.5 🍊 Contributes roomName and users. */
    @Override
    public void contribute(String roomId, SnapshotBuilder snapshot) {
        snapshot.put("roomName", rooms.name(roomId));
        snapshot.addAll("users", users.list(roomId));
    }
}
