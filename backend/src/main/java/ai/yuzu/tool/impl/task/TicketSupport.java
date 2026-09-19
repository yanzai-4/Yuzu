package ai.yuzu.tool.impl.task;

import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.internal.intake.NoticeService;
import ai.yuzu.room.RoomDirectory;
import ai.yuzu.room.RoomMember;
import ai.yuzu.task.Actor;
import ai.yuzu.task.ticket.Ticket;
import ai.yuzu.tool.spi.ToolContext;
import org.springframework.stereotype.Component;

/** v0.0.22 🍊 Shared helpers of the ticket tools: name lookups and assignee notices. */
@Component
public class TicketSupport {

    private final RoomDirectory directory;
    private final NoticeService notices;

    /** v0.0.22 🍊 Injects collaborators. */
    public TicketSupport(RoomDirectory directory, NoticeService notices) {
        this.directory = directory;
        this.notices = notices;
    }

    /** v0.0.22 🍊 A coworker (agent) of the room by exact name (leading @ allowed). */
    public AgentId agentNamed(String roomId, String name) {
        RoomMember m = member(roomId, name);
        if (!m.isAgent()) {
            throw new BadRequestException(m.name() + " is a human; tickets are assigned to AI coworkers.");
        }
        return AgentId.of(m.id());
    }

    /** v0.0.22 🍊 Any member (human or agent) as a task actor. */
    public Actor memberNamed(String roomId, String name) {
        RoomMember m = member(roomId, name);
        return m.isAgent() ? Actor.agent(AgentId.of(m.id()), m.name()) : Actor.human(m.id(), m.name());
    }

    /** v0.0.22 🍊 Tells the assignee about its new ticket. */
    public void notifyAssignee(ToolContext ctx, Ticket ticket) {
        notify(AgentId.of(ticket.assigneeId()), ctx, ctx.profile().name() + " assigned ticket " + ticket.id()
                + " to me: \"" + ticket.title() + "\"."
                + (ticket.detail() == null || ticket.detail().isBlank() ? "" : " Details: " + ticket.detail().strip())
                + (ticket.requesterName() == null ? "" : " Requested by " + ticket.requesterName() + ".")
                + " When the work is done I report to " + ctx.profile().name() + " in the group chat.");
    }

    /** v0.0.22 🍊 Delivers a notice from the acting agent to another agent. */
    public void notify(AgentId target, ToolContext ctx, String text) {
        notices.notify(target, ctx.profile().name() + " (" + ctx.profile().title() + ")", text,
                ctx.agent().traceId(), ctx.causalDepth() + 1);
    }

    /** v0.0.22 🍊 Member lookup by exact (case-insensitive) name. */
    private RoomMember member(String roomId, String rawName) {
        String name = rawName == null ? "" : rawName.strip().replaceFirst("^@", "");
        return directory.byName(roomId, name)
                .orElseThrow(() -> new BadRequestException("Nobody named \"" + name + "\" is in the workgroup."));
    }
}
