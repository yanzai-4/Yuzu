package ai.yuzu.room;

import java.util.List;

/** v0.0.5 🍊 Supplies one kind of room member (humans, agents) to the {@link RoomDirectory}. */
public interface RoomMemberSource {

    /** v0.0.5 🍊 Current members of this kind in the room. */
    List<RoomMember> members(String roomId);
}
