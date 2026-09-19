package ai.yuzu.task.web;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.task.ActorResolver;
import ai.yuzu.task.list.TaskListService;
import ai.yuzu.task.list.TaskListView;
import ai.yuzu.task.ticket.Ticket;
import ai.yuzu.task.ticket.TicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** v0.0.20 🍊 REST endpoints of the ticket board, the agents' task lists and the human "Approve & archive" button. */
@RestController
@RequestMapping("/api")
public class TaskController {

    private final TicketService tickets;
    private final TaskListService taskLists;
    private final ActorResolver actors;

    /** v0.0.20 🍊 Injects collaborators. */
    public TaskController(TicketService tickets, TaskListService taskLists, ActorResolver actors) {
        this.tickets = tickets;
        this.taskLists = taskLists;
        this.actors = actors;
    }

    /** v0.0.20 🍊 The room's tickets in creation order (contract type {@code Ticket[]}). */
    @GetMapping("/rooms/{roomId}/tickets")
    public List<Ticket> tickets(@PathVariable String roomId) {
        return tickets.list(roomId);
    }

    /** v0.0.20 🍊 An agent's current task list and latest archived ones (contract type {@code TaskListView}). */
    @GetMapping("/agents/{agentId}/tasks")
    public TaskListView tasks(@PathVariable String agentId) {
        return taskLists.current(AgentId.of(agentId));
    }

    /** v0.0.20 🍊 A human approves a finished list, which archives it; returns the agent's new view. */
    @PostMapping("/task-lists/{listId}/approve")
    public TaskListView approve(@PathVariable String listId, @Valid @RequestBody ApproveRequest request) {
        return taskLists.approve(listId, actors.human(request.userId()));
    }

    /** v0.0.20 🍊 Body of the approve call: the approving human. */
    public record ApproveRequest(@NotBlank String userId) {
    }
}
