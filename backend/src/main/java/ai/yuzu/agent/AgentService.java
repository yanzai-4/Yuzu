package ai.yuzu.agent;

import ai.yuzu.common.error.AgentLimitException;
import ai.yuzu.common.error.ConflictException;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import ai.yuzu.room.RoomDirectory;
import ai.yuzu.room.RoomMember;
import ai.yuzu.room.RoomMemberSource;
import ai.yuzu.room.RoomRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * v0.0.6 🍊 Hiring, editing, pausing and retiring agents; the agent roster source for the room directory.
 *
 * <p>At most {@link #MAX_AGENTS} present agents per workgroup (checked under a per-room lock). Profiles are
 * read on every module call, so they are cached (Caffeine) by id and by room and invalidated on change.</p>
 */
@Service
public class AgentService implements RoomMemberSource {

    /** v0.0.6 🍊 Maximum number of non-retired agents per workgroup. */
    public static final int MAX_AGENTS = 8;

    private final AgentRepository repository;
    private final RoomRepository rooms;
    private final RoomDirectory directory;
    private final SseHub hub;
    private final NaturalTime time;
    private final ObjectProvider<AgentLifecycleListener> listeners;
    private final Map<String, ReentrantLock> roomLocks = new ConcurrentHashMap<>();
    private final Cache<AgentId, AgentProfile> byId = Caffeine.newBuilder().maximumSize(1_000).build();
    private final Cache<String, List<AgentProfile>> byRoom = Caffeine.newBuilder().maximumSize(1_000).build();

    /** v0.0.6 🍊 Injects collaborators. */
    public AgentService(AgentRepository repository, RoomRepository rooms, RoomDirectory directory, SseHub hub,
                        NaturalTime time, ObjectProvider<AgentLifecycleListener> listeners) {
        this.repository = repository;
        this.rooms = rooms;
        this.directory = directory;
        this.hub = hub;
        this.time = time;
        this.listeners = listeners;
    }

    /** v0.0.6 🍊 Role presets offered in the UI. */
    public List<Role.RolePresetView> presets() {
        return Arrays.stream(Role.values()).map(Role::toView).toList();
    }

    /** v0.0.6 🍊 Present (non-retired) agents of a room, cached. */
    public List<AgentProfile> list(String roomId) {
        return byRoom.get(roomId, repository::findPresentByRoom);
    }

    /** v0.0.6 🍊 Cached profile lookup (any state). */
    public Optional<AgentProfile> find(AgentId agentId) {
        AgentProfile cached = byId.getIfPresent(agentId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<AgentProfile> loaded = repository.findById(agentId);
        loaded.ifPresent(p -> byId.put(agentId, p));
        return loaded;
    }

    /** v0.0.6 🍊 Profile or NOT_FOUND. */
    public AgentProfile require(AgentId agentId) {
        return find(agentId).orElseThrow(() -> new NotFoundException("Unknown agent " + agentId + ".")
                .forAgent(agentId.value()));
    }

    /** v0.0.6 🍊 Hires an agent with an automatically assigned citrus name. */
    public AgentProfile create(String roomId, CreateAgentRequest request) {
        return hire(roomId, request, null);
    }

    /** v0.0.6 🍊 Hires an agent with a specific citrus name (demo seed); falls back to any free name. */
    public AgentProfile createNamed(String roomId, CreateAgentRequest request, String citrusName) {
        return hire(roomId, request, citrusName);
    }

    /** v0.0.6 🍊 Edits title, scope, persona, permissions or limits (optimistic locking). */
    public AgentProfile update(AgentId agentId, UpdateAgentRequest request) {
        AgentProfile current = require(agentId);
        if (!current.isPresent()) {
            throw new ConflictException("Agent " + agentId + " is retired.").forAgent(agentId.value());
        }
        PermissionScope scope = new PermissionScope(
                request.permissions() != null ? request.permissions() : current.scope().permissions(),
                current.scope().limits().merge(request.limits()));
        AgentProfile next = new AgentProfile(current.agentId(), current.roomId(), current.name(), current.avatarKey(),
                current.color(), current.role(), orElse(request.title(), current.title()),
                orElse(request.scopeText(), current.scopeText()), orElse(request.persona(), current.persona()),
                scope, current.state(), current.version() + 1, current.createdAt(), time.nowInstant());
        return save(current, next);
    }

    /** v0.0.6 🍊 Pauses or resumes an agent (state is persisted; the runtime reacts via the listener). */
    public AgentProfile setState(AgentId agentId, AgentProfile.State state) {
        AgentProfile current = require(agentId);
        if (!current.isPresent() || state == AgentProfile.State.RETIRED) {
            throw new ConflictException("Use retire() for retired agents.").forAgent(agentId.value());
        }
        if (current.state() == state) {
            return current;
        }
        AgentProfile next = new AgentProfile(current.agentId(), current.roomId(), current.name(), current.avatarKey(),
                current.color(), current.role(), current.title(), current.scopeText(), current.persona(),
                current.scope(), state, current.version() + 1, current.createdAt(), time.nowInstant());
        return save(current, next);
    }

    /** v0.0.6 🍊 Retires an agent: frees its desk and citrus name; its data stays for history. */
    public void retire(AgentId agentId) {
        AgentProfile current = require(agentId);
        if (!current.isPresent()) {
            return;
        }
        repository.retire(agentId, time.nowInstant());
        invalidate(current);
        listeners.orderedStream().forEach(l -> l.onRetired(current));
        hub.publish(current.roomId(), EventType.AGENT_REMOVED, agentId.value(), Map.of("agentId", agentId.value()));
    }

    /** v0.0.6 🍊 Agent members for the room directory (mentions, rosters). */
    @Override
    public List<RoomMember> members(String roomId) {
        return list(roomId).stream()
                .map(p -> new RoomMember(p.agentId().value(), p.name(), RoomMember.Kind.AGENT, p.title(),
                        p.role().name()))
                .toList();
    }

    /** v0.0.6 🍊 Shared hiring path (limit check, id + name allocation, events). */
    private AgentProfile hire(String roomId, CreateAgentRequest request, String preferredName) {
        if (!rooms.exists(roomId)) {
            throw new NotFoundException("Room " + roomId + " does not exist.");
        }
        ReentrantLock lock = roomLocks.computeIfAbsent(roomId, id -> new ReentrantLock());
        lock.lock();
        AgentProfile created;
        try {
            if (repository.findPresentByRoom(roomId).size() >= MAX_AGENTS) {
                throw new AgentLimitException("This workgroup already has " + MAX_AGENTS + " coworkers.")
                        .with("max", MAX_AGENTS);
            }
            List<String> taken = repository.namesInRoom(roomId);
            List<String> humanNames = directory.members(roomId).stream().filter(m -> !m.isAgent())
                    .map(RoomMember::name).toList();
            List<String> unavailable = new ArrayList<>(taken);
            unavailable.addAll(humanNames);
            CitrusCatalog.Citrus citrus = Optional.ofNullable(preferredName)
                    .flatMap(CitrusCatalog::byName)
                    .filter(c -> unavailable.stream().noneMatch(n -> n.equalsIgnoreCase(c.name())))
                    .or(() -> CitrusCatalog.pickAvailable(unavailable))
                    .orElseThrow(() -> new ConflictException("No citrus names are left in this workgroup."));
            Role role = request.role();
            PermissionScope defaults = role.defaultScope();
            PermissionScope scope = new PermissionScope(
                    request.permissions() != null ? request.permissions() : defaults.permissions(),
                    defaults.limits().merge(request.limits()));
            Instant now = time.nowInstant();
            created = insertWithFreshId(roomId, citrus, role, orElse(request.title(), role.title()),
                    orElse(request.scopeText(), role.scopeText()), orElse(request.persona(), role.persona()),
                    scope, now);
        } finally {
            lock.unlock();
        }
        invalidate(created);
        listeners.orderedStream().forEach(l -> l.onCreated(created));
        hub.publish(roomId, EventType.AGENT_UPSERT, created.agentId().value(), created.toView(time));
        return created;
    }

    /** v0.0.6 🍊 Inserts the profile, regenerating the agent id on the rare collision. */
    private AgentProfile insertWithFreshId(String roomId, CitrusCatalog.Citrus citrus, Role role, String title,
                                           String scopeText, String persona, PermissionScope scope, Instant now) {
        for (int attempt = 0; attempt < 10; attempt++) {
            AgentId id = IdGen.newAgentId();
            if (repository.idExists(id)) {
                continue;
            }
            AgentProfile profile = new AgentProfile(id, roomId, citrus.name(), citrus.avatarKey(), citrus.color(),
                    role, title, scopeText, persona, scope, AgentProfile.State.ACTIVE, 0, now, now);
            try {
                repository.insert(profile);
                return profile;
            } catch (DuplicateKeyException e) {
                if (String.valueOf(e.getMessage()).contains("uk_room_name")) {
                    throw new ConflictException("The name " + citrus.name() + " is already used; please retry.");
                }
            }
        }
        throw new ConflictException("Could not allocate an agent id; please retry.");
    }

    /** v0.0.6 🍊 Persists an edit with optimistic locking, then refreshes caches and notifies. */
    private AgentProfile save(AgentProfile current, AgentProfile next) {
        if (!repository.update(next, current.version())) {
            invalidate(current);
            throw new ConflictException("The agent was changed by someone else; reload and retry.")
                    .forAgent(current.agentId().value());
        }
        invalidate(next);
        listeners.orderedStream().forEach(l -> l.onUpdated(next));
        hub.publish(next.roomId(), EventType.AGENT_UPSERT, next.agentId().value(), next.toView(time));
        return next;
    }

    /** v0.0.6 🍊 Drops cached views of the agent and its room (after commit of the change). */
    private void invalidate(AgentProfile profile) {
        byId.invalidate(profile.agentId());
        byRoom.invalidate(profile.roomId());
        directory.invalidate(profile.roomId());
    }

    /** v0.0.6 🍊 Uses the value unless it is null or blank. */
    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
