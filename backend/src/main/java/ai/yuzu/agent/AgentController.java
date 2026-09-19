package ai.yuzu.agent;

import ai.yuzu.agent.runtime.AgentControlService;
import ai.yuzu.agent.runtime.RoomControlView;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.monitor.AgentStatusView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** v0.0.6 🍊 REST endpoints to hire, edit, pause, resume, interrupt and retire agents. */
@RestController
@RequestMapping("/api")
public class AgentController {

    private final AgentService agents;
    private final AgentControlService control;
    private final NaturalTime time;

    /** v0.0.30 🍊 Injects collaborators (the control service answers pause / resume / interrupt / stop all). */
    public AgentController(AgentService agents, AgentControlService control, NaturalTime time) {
        this.agents = agents;
        this.control = control;
        this.time = time;
    }

    /** v0.0.6 🍊 Role presets for the hiring dialog. */
    @GetMapping("/roles")
    public List<Role.RolePresetView> roles() {
        return agents.presets();
    }

    /** v0.0.6 🍊 Present agents of a room. */
    @GetMapping("/rooms/{roomId}/agents")
    public List<AgentView> list(@PathVariable String roomId) {
        return agents.list(roomId).stream().map(a -> a.toView(time)).toList();
    }

    /** v0.0.6 🍊 Hires an agent; the citrus name and avatar are assigned automatically. */
    @PostMapping("/rooms/{roomId}/agents")
    public AgentView create(@PathVariable String roomId, @Valid @RequestBody CreateAgentRequest request) {
        return agents.create(roomId, request).toView(time);
    }

    /** v0.0.6 🍊 Edits an agent's job, persona, permissions or limits. */
    @PatchMapping("/agents/{agentId}")
    public AgentView update(@PathVariable String agentId, @Valid @RequestBody UpdateAgentRequest request) {
        return agents.update(AgentId.of(agentId), request).toView(time);
    }

    /** v0.0.6 🍊 Retires an agent. */
    @DeleteMapping("/agents/{agentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retire(@PathVariable String agentId) {
        agents.retire(AgentId.of(agentId));
    }

    /** v0.0.30 🍊 Pauses an agent (stops evaluating chat and running its main loop); returns its live status. */
    @PostMapping("/agents/{agentId}/pause")
    public AgentStatusView pause(@PathVariable String agentId) {
        return control.pause(AgentId.of(agentId));
    }

    /** v0.0.30 🍊 Resumes a paused agent; returns its live status. */
    @PostMapping("/agents/{agentId}/resume")
    public AgentStatusView resume(@PathVariable String agentId) {
        return control.resume(AgentId.of(agentId));
    }

    /** v0.0.30 🍊 Interrupts whatever the agent is doing right now (it stays active); returns its live status. */
    @PostMapping("/agents/{agentId}/interrupt")
    public AgentStatusView interrupt(@PathVariable String agentId) {
        return control.interrupt(AgentId.of(agentId), "Interrupted by a human");
    }

    /** v0.0.30 🍊 Stops every coworker of a room at once (pause + cancel what they are doing). */
    @PostMapping("/rooms/{roomId}/stop-all")
    public RoomControlView stopAll(@PathVariable String roomId) {
        return control.stopAll(roomId, "A human stopped every coworker");
    }

    /** v0.0.30 🍊 Lets every paused coworker of a room go back to work. */
    @PostMapping("/rooms/{roomId}/resume-all")
    public RoomControlView resumeAll(@PathVariable String roomId) {
        return control.resumeAll(roomId);
    }
}
