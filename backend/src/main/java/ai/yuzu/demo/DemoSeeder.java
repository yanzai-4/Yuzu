package ai.yuzu.demo;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Limits;
import ai.yuzu.agent.Permission;
import ai.yuzu.agent.Role;
import ai.yuzu.sim.email.FakeMailbox;
import ai.yuzu.sim.market.FakeBroker;
import ai.yuzu.sim.market.Quote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * v0.0.29 🍊 Seeds the collaboration demo: four role-matched coworkers plus the simulated world they need.
 *
 * <p>Idempotent and non-destructive. A coworker is hired only when its citrus name is free in the room, so
 * seeding an already-seeded room returns the same agents and never overwrites edits a human made. The
 * simulated world is seeded through the existing {@code ai.yuzu.sim} services, which have their own
 * one-shot bootstrap (mailboxes seed themselves on first read; market prices are generated, not stored).</p>
 */
@Service
public class DemoSeeder {

    /** v0.0.29 🍊 The workgroup the UI seeds when no room is given. */
    public static final String DEFAULT_ROOM = "room-0001";

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    /**
     * v0.0.29 🍊 One demo coworker: citrus name, role preset and the extras this demo needs.
     *
     * @param extraPermissions added on top of the role preset (the preset already fits the job)
     * @param limits           null keeps the preset's limits
     */
    private record Spec(String name, Role role, List<Permission> extraPermissions, Limits.LimitsPatch limits) {
    }

    private static final List<Spec> DEMO_TEAM = List.of(
            new Spec("Yuzu", Role.PROJECT_MANAGER, List.of(), null),
            new Spec("Lime", Role.RESEARCHER, List.of(), null),
            new Spec("Kumquat", Role.ENGINEER, List.of(), null),
            new Spec("Pomelo", Role.CUSTOMER_LIAISON, List.of(),
                    new Limits.LimitsPatch(List.of("acme.test", "example.com"), 10, null, null, null)));

    private final AgentService agents;
    private final FakeMailbox mailbox;
    private final FakeBroker broker;
    private final Map<String, ReentrantLock> roomLocks = new ConcurrentHashMap<>();

    /** v0.0.29 🍊 Injects collaborators. */
    public DemoSeeder(AgentService agents, FakeMailbox mailbox, FakeBroker broker) {
        this.agents = agents;
        this.mailbox = mailbox;
        this.broker = broker;
    }

    /**
     * v0.0.29 🍊 Seeds a room and returns its four demo coworkers in demo order (existing ones untouched).
     *
     * <p>Serialized per room so two concurrent seeds cannot hire the same coworker twice.</p>
     */
    public List<AgentProfile> seed(String roomId) {
        String room = roomId == null || roomId.isBlank() ? DEFAULT_ROOM : roomId.strip();
        ReentrantLock lock = roomLocks.computeIfAbsent(room, id -> new ReentrantLock());
        lock.lock();
        List<AgentProfile> team;
        try {
            List<AgentProfile> present = agents.list(room);
            List<AgentProfile> result = new ArrayList<>(DEMO_TEAM.size());
            for (Spec spec : DEMO_TEAM) {
                result.add(named(present, spec.name()).orElseGet(() -> hire(room, spec)));
            }
            team = List.copyOf(result);
        } finally {
            lock.unlock();
        }
        seedSimulatedWorld(team);
        return team;
    }

    /** v0.0.29 🍊 The simulated market symbols the demo can trade and quote (generated, nothing to store). */
    public List<String> symbols() {
        return broker.quotes().stream().map(Quote::symbol).toList();
    }

    /** v0.0.29 🍊 Hires one demo coworker with its role preset plus the demo's extras. */
    private AgentProfile hire(String roomId, Spec spec) {
        List<Permission> permissions = spec.extraPermissions().isEmpty() ? null
                : merged(spec.role(), spec.extraPermissions());
        AgentProfile hired = agents.createNamed(roomId, new CreateAgentRequest(spec.role(), null, null, null,
                permissions, spec.limits()), spec.name());
        if (!hired.name().equalsIgnoreCase(spec.name())) {
            log.warn("The demo name {} is taken in {}; hired {} as {} instead (seeding again will hire another one)",
                    spec.name(), roomId, hired.name(), spec.role());
        }
        return hired;
    }

    /** v0.0.29 🍊 The role preset's permissions plus the extras, without duplicates. */
    private static List<Permission> merged(Role role, List<Permission> extras) {
        List<Permission> all = new ArrayList<>(role.defaultScope().permissions());
        extras.stream().filter(p -> !all.contains(p)).forEach(all::add);
        return List.copyOf(all);
    }

    /**
     * v0.0.29 🍊 Seeds the simulated world: customer e-mails for everyone who may read mail, and market data.
     *
     * <p>Reuses the bootstrap that already lives in {@code ai.yuzu.sim}: the first read of a mailbox inserts
     * its seed e-mails once, and quotes are derived from the clock, so both are safe to call again.</p>
     */
    private void seedSimulatedWorld(List<AgentProfile> team) {
        List<AgentProfile> readers = team.stream().filter(agent -> agent.scope().has(Permission.EMAIL_READ)).toList();
        readers.forEach(agent -> mailbox.inbox(agent.agentId()));
        log.info("Demo world ready: mailboxes of {}, market symbols {}",
                readers.stream().map(AgentProfile::name).toList(), symbols());
    }

    /** v0.0.29 🍊 The present agent with this citrus name (names are unique per room, case-insensitive). */
    private static Optional<AgentProfile> named(List<AgentProfile> present, String name) {
        return present.stream()
                .filter(agent -> agent.name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))
                .findFirst();
    }
}
