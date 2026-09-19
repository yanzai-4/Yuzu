package ai.yuzu.tool.impl.task;

import ai.yuzu.agent.Permission;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.Nullable;
import ai.yuzu.task.Actor;
import ai.yuzu.task.ticket.NewTicket;
import ai.yuzu.task.ticket.Ticket;
import ai.yuzu.task.ticket.TicketService;
import ai.yuzu.tool.spi.PermissionGuard;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.tool.spi.ToolSpec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/** v0.0.22 🍊 Creates a ticket on the board, optionally assigned at once (the assignee is notified). */
@Component
public class TicketCreateTool implements Tool<TicketCreateTool.Args> {

    /** v0.0.22 🍊 Arguments. */
    public record Args(@Desc("Short title of the work") String title,
                       @Desc("What exactly must be done, including acceptance criteria") String detail,
                       @Nullable @Desc("Exact name of the coworker to assign it to; null to leave it open") String assignee,
                       @Nullable @Desc("Exact name of the human who asked for this work; null if nobody asked") String requester) {
    }

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("ticket_create",
            "Create a ticket on the ticket board, optionally assigning it to a coworker (who is notified).",
            Args.class, Set.of(Permission.TASK_ASSIGN), Risk.LOW, Duration.ofSeconds(15), true,
            "the ticket board confirmed");

    private final TicketService tickets;
    private final TicketSupport support;
    private final PermissionGuard guard;
    private final NaturalTime time;

    /** v0.0.22 🍊 Injects collaborators. */
    public TicketCreateTool(TicketService tickets, TicketSupport support, PermissionGuard guard, NaturalTime time) {
        this.tickets = tickets;
        this.support = support;
        this.guard = guard;
        this.time = time;
    }

    /** v0.0.22 🍊 Static description. */
    @Override
    public ToolSpec<Args> spec() {
        return SPEC;
    }

    /** v0.0.22 🍊 Creates (and assigns) the ticket in code; returns its id. */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.TASK_ASSIGN);
        String roomId = ctx.profile().roomId();
        NewTicket request = NewTicket.of(args.title(), args.detail());
        if (args.assignee() != null && !args.assignee().isBlank()) {
            request = request.assignedTo(support.agentNamed(roomId, args.assignee()));
        }
        if (args.requester() != null && !args.requester().isBlank()) {
            request = request.requestedBy(support.memberNamed(roomId, args.requester()));
        }
        Ticket ticket = tickets.create(roomId, Actor.of(ctx.profile()), request);
        if (ticket.assigneeId() != null) {
            support.notifyAssignee(ctx, ticket);
        }
        return ToolResult.ok("Created ticket " + ticket.id() + " \"" + ticket.title() + "\""
                + (ticket.assigneeId() == null ? " (open, not assigned yet)."
                : " and assigned it to " + args.assignee().strip() + ", who has been notified."), time.nowInstant());
    }
}
