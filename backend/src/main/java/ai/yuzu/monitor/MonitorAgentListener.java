package ai.yuzu.monitor;

import ai.yuzu.agent.AgentLifecycleListener;
import ai.yuzu.agent.AgentProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/** v0.0.12 🍊 Keeps the status board in sync with boot, hires, edits, pauses and retirements, and records them as SYSTEM events. */
@Component
public class MonitorAgentListener implements AgentLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(MonitorAgentListener.class);

    private final AgentStatusBoard board;
    private final MonitorService monitor;
    private final AgentLookup lookup;

    /** v0.0.12 🍊 Injects the board, the reporting API and the registry view. */
    public MonitorAgentListener(AgentStatusBoard board, MonitorService monitor, AgentLookup lookup) {
        this.board = board;
        this.monitor = monitor;
        this.lookup = lookup;
    }

    /** v0.0.12 🍊 Tracks every present agent once the application is ready, so rooms and pause flags survive a restart. */
    @EventListener(ApplicationReadyEvent.class)
    public void registerPresentAgents() {
        try {
            lookup.presentAgents().forEach((agentId, placement) ->
                    board.register(agentId, placement.roomId(), placement.paused()));
        } catch (RuntimeException e) {
            log.warn("Monitor could not preload the present agents; they register on first use", e);
        }
    }

    /** v0.0.12 🍊 Starts tracking a new agent. */
    @Override
    public void onCreated(AgentProfile profile) {
        try {
            board.register(profile.agentId(), profile.roomId(), paused(profile));
            monitor.info(profile.agentId(), ModuleKind.SYSTEM, "Joined the team as " + profile.title() + ".",
                    Map.of("name", profile.name(), "role", profile.role().name()));
        } catch (RuntimeException e) {
            log.warn("Monitor could not register {}", profile.agentId(), e);
        }
    }

    /** v0.0.12 🍊 Reflects PAUSED / ACTIVE and records the change. */
    @Override
    public void onUpdated(AgentProfile profile) {
        try {
            boolean paused = paused(profile);
            boolean pauseChanged = board.register(profile.agentId(), profile.roomId(), paused);
            String text = !pauseChanged ? "Profile updated." : paused ? "Paused." : "Resumed.";
            monitor.info(profile.agentId(), ModuleKind.SYSTEM, text,
                    Map.of("state", profile.state().name(), "version", profile.version()));
        } catch (RuntimeException e) {
            log.warn("Monitor could not update {}", profile.agentId(), e);
        }
    }

    /** v0.0.12 🍊 Records the retirement and stops tracking the agent. */
    @Override
    public void onRetired(AgentProfile profile) {
        try {
            monitor.info(profile.agentId(), ModuleKind.SYSTEM, "Retired.", Map.of("name", profile.name()));
            board.remove(profile.agentId());
        } catch (RuntimeException e) {
            log.warn("Monitor could not retire {}", profile.agentId(), e);
        }
    }

    /** v0.0.12 🍊 True when the profile is paused. */
    private static boolean paused(AgentProfile profile) {
        return profile.state() == AgentProfile.State.PAUSED;
    }
}
