package ai.yuzu.room;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * v0.0.5 🍊 Cached list of everyone in a room (humans + agents), used for @mentions and rosters.
 *
 * <p>Read on every chat message by every agent, so it is cached per room (Caffeine) and invalidated
 * explicitly whenever a human joins or an agent is created, edited or retired.</p>
 */
@Component
public class RoomDirectory {

    private final ObjectProvider<RoomMemberSource> sources;
    private final Cache<String, List<RoomMember>> cache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    /** v0.0.5 🍊 Collects every member source (lazy to avoid construction cycles). */
    public RoomDirectory(ObjectProvider<RoomMemberSource> sources) {
        this.sources = sources;
    }

    /** v0.0.5 🍊 All members of a room, humans first then agents, each in creation order. */
    public List<RoomMember> members(String roomId) {
        return cache.get(roomId, id -> {
            List<RoomMember> all = new ArrayList<>();
            sources.orderedStream().forEach(source -> all.addAll(source.members(id)));
            return Collections.unmodifiableList(all);
        });
    }

    /** v0.0.5 🍊 Finds a member by display name (case-insensitive). */
    public Optional<RoomMember> byName(String roomId, String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        return members(roomId).stream().filter(m -> m.name().toLowerCase(Locale.ROOT).equals(wanted)).findFirst();
    }

    /** v0.0.5 🍊 Finds a member by id. */
    public Optional<RoomMember> byId(String roomId, String id) {
        return members(roomId).stream().filter(m -> m.id().equals(id)).findFirst();
    }

    /** v0.0.5 🍊 Drops the cached member list of a room (call after any membership change). */
    public void invalidate(String roomId) {
        cache.invalidate(roomId);
    }
}
