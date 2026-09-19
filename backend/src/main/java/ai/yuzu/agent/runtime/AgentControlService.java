package ai.yuzu.agent.runtime;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.monitor.AgentStatusBoard;
import ai.yuzu.monitor.AgentStatusView;
import ai.yuzu.monitor.ModuleKind;
import ai.yuzu.monitor.MonitorService;
import ai.yuzu.monitor.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * v0.0.30 🍊 The humans' emergency brake: interrupt, pause, resume one coworker, or stop / resume a whole room.
 *
 * <p>Cancellation is cooperative. {@link AgentRuntime#interrupt(String)} swaps in a fresh {@code CancelToken}
 * and cancels the old one, which interrupts every thread bound to it: a blocking {@code HttpClient} read aborts
 * at once and streaming sinks stop at the next delta, so an interrupt takes effect in milliseconds and never
 * leaves the consciousness pool owned by a dead run (the main loop releases ownership through
 * {@code ConsciousnessPool.abandon()}). Because a fresh token is installed by the same atomic swap, the next
 * task of the agent starts uncancelled.</p>
 *
 * <p>Every action is reported to the monitor as a SYSTEM span that terminates with CANCELLED, so the trace
 * waterfall, the desk bubbles and every connected client see what a human did. Pause and resume also persist
 * the agent state through {@link AgentService}, which publishes {@code agent.upsert} to the room.</p>
 */
@Service
public class AgentControlService {

    private static final Logger log = LoggerFactory.getLogger(AgentControlService.class);

    private final AgentService agents;
    private final AgentRuntimeManager runtimes;
    private final AgentStatusBoard statuses;
    private final MonitorService monitor;
    private final NaturalTime time;
    // Room-wide actions are serialized per room so two humans pressing "stop all" cannot interleave.
    private final Map<String, ReentrantLock> roomLocks = new ConcurrentHashMap<>();

    /** v0.0.30 🍊 Injects collaborators. */
    public AgentControlService(AgentService agents, AgentRuntimeManager runtimes, AgentStatusBoard statuses,
                               MonitorService monitor, NaturalTime time) {
        this.agents = agents;
        this.runtimes = runtimes;
        this.statuses = statuses;
        this.monitor = monitor;
        this.time = time;
    }

    /** v0.0.30 🍊 Cancels whatever the agent is doing right now; it stays active and picks up the next message. */
    public AgentStatusView interrupt(AgentId agentId, String reason) {
        AgentRuntime runtime = runtimes.require(agentId);
        String why = reason == null || reason.isBlank() ? "Interrupted by a human" : reason;
        runtime.interrupt(why);
        report(agentId, "Interrupt requested by a human.", why);
        return statuses.status(agentId);
    }

    /** v0.0.30 🍊 Pauses the agent (no chat triage, no main runs) and cancels what it is doing right now. */
    public AgentStatusView pause(AgentId agentId) {
        pause(agentId, "Paused by a human");
        return statuses.status(agentId);
    }

    /** v0.0.30 🍊 Resumes a paused agent; queued messages start a new main run immediately. */
    public AgentStatusView resume(AgentId agentId) {
        agents.setState(agentId, AgentProfile.State.ACTIVE);
        return statuses.status(agentId);
    }

    /** v0.0.30 🍊 Stops every coworker of a room at once (the console's big red button). */
    public RoomControlView stopAll(String roomId, String reason) {
        String why = reason == null || reason.isBlank() ? "A human stopped every coworker" : reason;
        return roomAction(roomId, RoomControlView.STOP_ALL, profile -> pause(profile.agentId(), why));
    }

    /** v0.0.30 🍊 Lets every paused coworker of a room resume. */
    public RoomControlView resumeAll(String roomId) {
        return roomAction(roomId, RoomControlView.RESUME_ALL, profile -> {
            if (profile.state() != AgentProfile.State.PAUSED) {
                return false;
            }
            agents.setState(profile.agentId(), AgentProfile.State.ACTIVE);
            return true;
        });
    }

    /** v0.0.30 🍊 Pauses one agent and cancels its in-flight work; true when it was still active. */
    private boolean pause(AgentId agentId, String reason) {
        boolean changed = agents.require(agentId).state() != AgentProfile.State.PAUSED;
        agents.setState(agentId, AgentProfile.State.PAUSED);
        // The lifecycle listener already cancels on pause; doing it here too keeps the guarantee local and
        // is safe because interrupt() atomically installs a fresh token every time.
        runtimes.find(agentId).ifPresent(runtime -> runtime.interrupt(reason));
        report(agentId, "Pause requested by a human.", reason);
        return changed;
    }

    /** v0.0.30 🍊 Applies an action to every present coworker of a room under the room lock. */
    private RoomControlView roomAction(String roomId, String action, Predicate<AgentProfile> apply) {
        ReentrantLock lock = roomLocks.computeIfAbsent(roomId, id -> new ReentrantLock());
        lock.lock();
        try {
            List<AgentStatusView> result = new ArrayList<>();
            int affected = 0;
            for (AgentProfile profile : agents.list(roomId)) {
                try {
                    if (apply.test(profile)) {
                        affected++;
                    }
                    result.add(statuses.status(profile.agentId()));
                } catch (RuntimeException e) {
                    // One coworker failing must never stop the others from being stopped.
                    log.warn("🍊 {} failed for {}", action, profile.agentId(), e);
                }
            }
            return new RoomControlView(roomId, action, affected, result, time.compact(time.nowInstant()));
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.30 🍊 Records the human's action as a SYSTEM span that ends CANCELLED (trace + live stream). */
    private void report(AgentId agentId, String requested, String reason) {
        Span span = monitor.start(agentId, ModuleKind.SYSTEM, requested, null, null);
        if (span != null) {
            span.cancelled("Stopped: " + reason);
        }
    }
}
