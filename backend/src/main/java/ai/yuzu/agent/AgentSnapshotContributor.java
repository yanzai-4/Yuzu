package ai.yuzu.agent;

import ai.yuzu.bootstrap.SnapshotBuilder;
import ai.yuzu.bootstrap.SnapshotContributor;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.monitor.AgentStatusBoard;
import org.springframework.stereotype.Component;

import java.util.List;

/** v0.0.12 🍊 Adds the room's agents and their live statuses (from the monitor's status board) to the snapshot. */
@Component
public class AgentSnapshotContributor implements SnapshotContributor {

    private final AgentService agents;
    private final AgentStatusBoard board;
    private final NaturalTime time;

    /** v0.0.12 🍊 Injects collaborators. */
    public AgentSnapshotContributor(AgentService agents, AgentStatusBoard board, NaturalTime time) {
        this.agents = agents;
        this.board = board;
        this.time = time;
    }

    /** v0.0.12 🍊 Contributes agents and their live statuses. */
    @Override
    public void contribute(String roomId, SnapshotBuilder snapshot) {
        List<AgentProfile> present = agents.list(roomId);
        snapshot.addAll("agents", present.stream().map(p -> p.toView(time)).toList());
        snapshot.addAll("statuses", board.snapshot(present.stream().map(p -> p.agentId().value()).toList()));
    }
}
