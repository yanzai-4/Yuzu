package ai.yuzu.tool.impl.task;

import ai.yuzu.agent.Permission;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.task.Actor;
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

/** v0.0.22 🍊 Assigns an open ticket to a coworker (who is notified). */
@Component
public class TicketAssignTool implements Tool<TicketAssignTool.Args> {

    /** v0.0.22 🍊 Arguments. */
    public record Args(@Desc("The ticket id") String ticketId,
                       @Desc("Exact name of the coworker to assign it to") String assignee) {
    }

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("ticket_assign",
            "Assign an open ticket to a coworker (who is notified).",
            Args.class, Set.of(Permission.TASK_ASSIGN), Risk.LOW, Duration.ofSeconds(15), true,
            "the ticket board confirmed");

    private final TicketService tickets;
    private final TicketSupport support;
    private final PermissionGuard guard;
    private final NaturalTime time;

    /** v0.0.22 🍊 Injects collaborators. */
    public TicketAssignTool(TicketService tickets, TicketSupport support, PermissionGuard guard, NaturalTime time) {
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

    /** v0.0.22 🍊 Assigns in code and notifies the assignee. */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.TASK_ASSIGN);
        Ticket ticket = tickets.assign(args.ticketId().strip(),
                support.agentNamed(ctx.profile().roomId(), args.assignee()), Actor.of(ctx.profile()));
        support.notifyAssignee(ctx, ticket);
        return ToolResult.ok("Assigned ticket " + ticket.id() + " \"" + ticket.title() + "\" to "
                + args.assignee().strip() + ", who has been notified.", time.nowInstant());
    }
}
