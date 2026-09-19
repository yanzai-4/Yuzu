package ai.yuzu.agent;

import ai.yuzu.bootstrap.SnapshotBuilder;
import ai.yuzu.bootstrap.SnapshotContributor;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.monitor.AgentStatusView;
import org.springframework.stereotype.Component;

import java.util.List;

/** v0.0.6 🍊 Adds the room's agents (and their idle statuses until the monitor reports) to the snapshot. */
@Component
public class AgentSnapshotContributor implements SnapshotContributor {

    private final AgentService agents;
    private final NaturalTime time;

    /** v0.0.6 🍊 Injects collaborators. */
    public AgentSnapshotContributor(AgentService agents, NaturalTime time) {
        this.agents = agents;
        this.time = time;
    }

    /** v0.0.6 🍊 Contributes agents and statuses. */
    @Override
    public void contribute(String roomId, SnapshotBuilder snapshot) {
        List<AgentProfile> present = agents.list(roomId);
        snapshot.addAll("agents", present.stream().map(p -> p.toView(time)).toList());
        String now = time.compact(time.nowInstant());
        snapshot.addAll("statuses", present.stream()
                .map(p -> AgentStatusView.idle(p.agentId().value(), p.state() == AgentProfile.State.PAUSED, now))
                .toList());
    }
}
