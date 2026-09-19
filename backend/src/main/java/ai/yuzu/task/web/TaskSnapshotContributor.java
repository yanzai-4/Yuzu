package ai.yuzu.task.web;

import ai.yuzu.agent.AgentService;
import ai.yuzu.bootstrap.SnapshotBuilder;
import ai.yuzu.bootstrap.SnapshotContributor;
import ai.yuzu.task.list.TaskListService;
import ai.yuzu.task.ticket.TicketService;
import org.springframework.stereotype.Component;

/** v0.0.20 🍊 Adds the room's tickets and one TaskListView per present agent to the bootstrap snapshot. */
@Component
public class TaskSnapshotContributor implements SnapshotContributor {

    private final TicketService tickets;
    private final TaskListService taskLists;
    private final AgentService agents;

    /** v0.0.20 🍊 Injects collaborators. */
    public TaskSnapshotContributor(TicketService tickets, TaskListService taskLists, AgentService agents) {
        this.tickets = tickets;
        this.taskLists = taskLists;
        this.agents = agents;
    }

    /** v0.0.20 🍊 Contributes {@code tickets} and {@code taskLists}. */
    @Override
    public void contribute(String roomId, SnapshotBuilder snapshot) {
        snapshot.addAll("tickets", tickets.list(roomId));
        snapshot.addAll("taskLists", agents.list(roomId).stream()
                .map(profile -> taskLists.current(profile.agentId()))
                .toList());
    }
}
