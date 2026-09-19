package ai.yuzu.room;

import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.ConflictException;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * v0.0.5 🍊 Human membership: join by username only (hackathon rule), lookups, and the human roster source.
 *
 * <p>Re-joining with an existing username returns the same user. Usernames may not clash with agent
 * names or the reserved word "all", so @mentions stay unambiguous.</p>
 */
@Service
public class HumanUserService implements RoomMemberSource {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 _.\\-]{0,39}$");
    private static final List<String> COLORS = List.of(
            "#2563eb", "#7c3aed", "#db2777", "#0891b2", "#059669", "#ea580c", "#4f46e5", "#be123c");

    private final HumanUserRepository users;
    private final RoomRepository rooms;
    private final RoomDirectory directory;
    private final SseHub hub;
    private final NaturalTime time;

    /** v0.0.5 🍊 Injects collaborators. */
    public HumanUserService(HumanUserRepository users, RoomRepository rooms, RoomDirectory directory, SseHub hub,
                            NaturalTime time) {
        this.users = users;
        this.rooms = rooms;
        this.directory = directory;
        this.hub = hub;
        this.time = time;
    }

    /** v0.0.5 🍊 Joins a room with a username; returns the existing user when the name was used before. */
    public UserView join(String roomId, String rawUsername) {
        String username = rawUsername == null ? "" : rawUsername.trim().replaceAll("\\s+", " ");
        if (!USERNAME.matcher(username).matches()) {
            throw new BadRequestException("Usernames are 1-40 letters, digits, spaces, '.', '_' or '-'.")
                    .with("username", username);
        }
        if (username.equalsIgnoreCase("all")) {
            throw new BadRequestException("'all' is reserved for @all mentions.");
        }
        if (!rooms.exists(roomId)) {
            throw new NotFoundException("Room " + roomId + " does not exist.");
        }
        var existing = users.findByUsername(roomId, username);
        if (existing.isPresent()) {
            users.touch(existing.get().id(), time.nowInstant());
            return existing.get();
        }
        directory.byName(roomId, username).filter(RoomMember::isAgent).ifPresent(agent -> {
            throw new ConflictException("'" + username + "' is the name of an AI coworker; pick another name.");
        });
        UserView user = insertWithFreshId(roomId, username);
        directory.invalidate(roomId);
        hub.publish(roomId, EventType.USER_JOINED, null, user);
        return user;
    }

    /** v0.0.5 🍊 Finds a user or throws NOT_FOUND. */
    public UserView require(String userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Unknown user " + userId + "."));
    }

    /** v0.0.5 🍊 All users of a room. */
    public List<UserView> list(String roomId) {
        return users.findByRoom(roomId);
    }

    /** v0.0.5 🍊 Human members for the room directory. */
    @Override
    public List<RoomMember> members(String roomId) {
        return users.findByRoom(roomId).stream()
                .map(u -> new RoomMember(u.id(), u.username(), RoomMember.Kind.HUMAN, "Human teammate", "HUMAN"))
                .toList();
    }

    /** v0.0.5 🍊 Inserts the user, retrying on the rare random-id collision. */
    private UserView insertWithFreshId(String roomId, String username) {
        String color = COLORS.get(Math.floorMod(username.toLowerCase(Locale.ROOT).hashCode(), COLORS.size()));
        for (int attempt = 0; attempt < 5; attempt++) {
            UserView user = new UserView(IdGen.newUserId(), username, color, roomId);
            try {
                users.insert(user, time.nowInstant());
                return user;
            } catch (DuplicateKeyException e) {
                var raced = users.findByUsername(roomId, username);
                if (raced.isPresent()) {
                    return raced.get();
                }
            }
        }
        throw new ConflictException("Could not allocate a user id; please retry.");
    }
}
