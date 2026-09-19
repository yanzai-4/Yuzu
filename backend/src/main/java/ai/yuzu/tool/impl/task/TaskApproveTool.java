package ai.yuzu.tool.impl.task;

import ai.yuzu.agent.Permission;
import ai.yuzu.common.error.ConflictException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.task.Actor;
import ai.yuzu.task.list.TaskList;
import ai.yuzu.task.list.TaskListService;
import ai.yuzu.task.list.TaskListStatus;
import ai.yuzu.tool.spi.PermissionGuard;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.tool.spi.ToolSpec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/** v0.0.22 🍊 Approves a coworker's finished task list that I published, so it is archived (the coworker is notified). */
@Component
public class TaskApproveTool implements Tool<TaskApproveTool.Args> {

    /** v0.0.22 🍊 Arguments. */
    public record Args(@Desc("Exact name of the coworker whose finished task list I approve") String coworker) {
    }

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("task_approve",
            "Approve a coworker's finished task list that I published (it is then archived and its ticket approved).",
            Args.class, Set.of(Permission.TASK_APPROVE), Risk.LOW, Duration.ofSeconds(15), true,
            "the task system confirmed");

    private final TaskListService lists;
    private final TicketSupport support;
    private final PermissionGuard guard;
    private final NaturalTime time;

    /** v0.0.22 🍊 Injects collaborators. */
    public TaskApproveTool(TaskListService lists, TicketSupport support, PermissionGuard guard, NaturalTime time) {
        this.lists = lists;
        this.support = support;
        this.guard = guard;
        this.time = time;
    }

    /** v0.0.22 🍊 Static description. */
    @Override
    public ToolSpec<Args> spec() {
        return SPEC;
    }

    /** v0.0.22 🍊 Approves in code (the task service re-checks that I am the publisher). */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.TASK_APPROVE);
        AgentId coworker = support.agentNamed(ctx.profile().roomId(), args.coworker());
        TaskList list = lists.current(coworker).current();
        if (list == null || list.status() != TaskListStatus.AWAITING_APPROVAL) {
            throw new ConflictException(args.coworker().strip() + " has no finished task list waiting for approval.");
        }
        lists.approve(list.id(), Actor.of(ctx.profile()));
        support.notify(coworker, ctx, ctx.profile().name() + " approved my task list \"" + list.goal()
                + "\"; it is archived now.");
        return ToolResult.ok("Approved " + args.coworker().strip() + "'s task list \"" + list.goal()
                + "\"; it is archived.", time.nowInstant());
    }
}
