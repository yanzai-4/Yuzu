package ai.yuzu.task.ticket;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.Permission;
import ai.yuzu.agent.Role;
import ai.yuzu.chat.AuthorKind;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.chat.ChatMessageListener;
import ai.yuzu.chat.ChatPost;
import ai.yuzu.chat.ChatService;
import ai.yuzu.chat.MessageKind;
import ai.yuzu.chat.StreamState;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.external.chat.LoopGuard;
import ai.yuzu.internal.intake.NoticeService;
import ai.yuzu.room.RoomDirectory;
import ai.yuzu.room.RoomMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * v0.0.34 🍊 Code (not a prompt) that catches a coworker taking on a human request privately, without a ticket.
 *
 * <p>Every group message is checked: when a coworker who may not assign work tells a human "on it, I'll do
 * that", has no open ticket, was not @mentioned by that human (a direct request IS its own work) and did not
 * route the request through the project manager, the PM @mentions the coworker once and asks for a ticket. The PM's mind is told as well (a governance
 * notice), so it can create and assign that ticket.</p>
 *
 * <p>Loop safety, on top of the five existing layers: at most one warning per coworker and episode, the
 * warning is counted in {@link LoopGuard}'s room budget, deep chains ({@link LoopGuard#MAX_DEPTH}) are left
 * alone, and a reply carrying the {@code closure} marker silences the governor until a human speaks again.</p>
 */
@Component
public class TicketGovernor implements ChatMessageListener {

    /** v0.0.29 🍊 Statuses this ticket assigns to work that is already visible to the team. */
    private static final Set<TicketStatus> LIVE = Set.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS,
            TicketStatus.DONE);

    /** v0.0.29 🍊 Phrases with which a coworker takes work on ("on it", "I'll build it", ...). */
    private static final Pattern COMMITMENT = Pattern.compile(
            "\\b(on it|i'?ll |i will |i am going to |i'?m going to |i'?m starting|i am starting|i'?ve started"
                    + "|i started|i'?m building|i am building|i'?m working on|i am working on|working on it"
                    + "|let me (do|build|write|handle|take)|consider it done|leave it (to|with) me"
                    + "|i can (do|build|write|handle|take) (it|that|this))",
            Pattern.CASE_INSENSITIVE);

    /** v0.0.29 🍊 Phrases that show the coworker is routing the request properly (then the governor is quiet). */
    private static final Pattern ROUTED = Pattern.compile(
            "\\b(ticket|assign|check with|ask the (pm|project manager)|route (it|this|that))",
            Pattern.CASE_INSENSITIVE);

    private static final Logger log = LoggerFactory.getLogger(TicketGovernor.class);

    /** v0.0.29 🍊 How far the governor got with one coworker in the current episode. */
    private enum Stage { WARNED, CLOSED }

    private final AgentService agents;
    private final RoomDirectory directory;
    private final TicketService tickets;
    private final NoticeService notices;
    private final LoopGuard loopGuard;
    private final ChatService chat;
    private final Map<String, Stage> stages = new ConcurrentHashMap<>();
    /** v0.0.34 🍊 Agents the latest human message of a room @mentioned: a direct request IS their own work. */
    private final Map<String, Set<String>> addressed = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> roomLocks = new ConcurrentHashMap<>();

    /** v0.0.29 🍊 Injects collaborators (chat lazily: the chat service fans out to this listener). */
    public TicketGovernor(AgentService agents, RoomDirectory directory, TicketService tickets, NoticeService notices,
                          LoopGuard loopGuard, @Lazy ChatService chat) {
        this.agents = agents;
        this.directory = directory;
        this.tickets = tickets;
        this.notices = notices;
        this.loopGuard = loopGuard;
        this.chat = chat;
    }

    /** v0.0.29 🍊 Inspects every new group message; a human message starts a fresh episode in that room. */
    @Override
    public void onMessage(ChatMessage message) {
        if (message.authorKind() == AuthorKind.HUMAN) {
            stages.entrySet().removeIf(entry ->
                    entry.getKey().startsWith(message.roomId() + "|") && entry.getValue() == Stage.CLOSED);
            addressed.put(message.roomId(), Set.copyOf(message.mentions()));
            return;
        }
        if (message.authorKind() != AuthorKind.AGENT || message.kind() != MessageKind.TEXT || !message.fanout()) {
            return;
        }
        ReentrantLock lock = roomLocks.computeIfAbsent(message.roomId(), id -> new ReentrantLock());
        lock.lock();
        try {
            inspect(message);
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.29 🍊 Decides what this coworker's message means for the current episode (under the room lock). */
    private void inspect(ChatMessage message) {
        String key = message.roomId() + "|" + message.authorId();
        Stage stage = stages.get(key);
        if (stage == Stage.WARNED && message.closure()) {
            stages.put(key, Stage.CLOSED);
            log.info("Ticket governor: {} acknowledged the reminder, staying quiet", message.authorName());
            return;
        }
        if (stage != null) {
            return;
        }
        AgentProfile author = agents.find(AgentId.of(message.authorId())).orElse(null);
        if (author == null || !privateIntake(message, author)) {
            return;
        }
        pmOf(message.roomId(), author).ifPresent(pm -> warn(pm, author, message, key));
    }

    /** v0.0.29 🍊 True when the message is a coworker promising a human work that has no ticket. */
    private boolean privateIntake(ChatMessage message, AgentProfile author) {
        if (message.causalDepth() >= LoopGuard.MAX_DEPTH || message.mentionAll()) {
            return false;
        }
        if (author.role() == Role.PROJECT_MANAGER || author.scope().has(Permission.TASK_ASSIGN)) {
            return false;
        }
        // A human who @mentions a coworker by name gives it the work directly; the PM has nothing to correct.
        if (addressed.getOrDefault(message.roomId(), Set.of()).contains(author.agentId().value())) {
            return false;
        }
        String content = message.content();
        if (!COMMITMENT.matcher(content).find() || ROUTED.matcher(content).find()) {
            return false;
        }
        return mentionsAHuman(message) && !mentionsThePm(message) && !hasLiveTicket(author);
    }

    /** v0.0.29 🍊 True when the message answers a human directly (the request never reached the PM). */
    private boolean mentionsAHuman(ChatMessage message) {
        return directory.members(message.roomId()).stream()
                .anyMatch(member -> !member.isAgent() && message.mentions().contains(member.id()));
    }

    /** v0.0.29 🍊 True when the coworker looped the project manager in (then it is not private intake). */
    private boolean mentionsThePm(ChatMessage message) {
        return directory.members(message.roomId()).stream()
                .filter(RoomMember::isAgent)
                .filter(member -> Role.PROJECT_MANAGER.name().equals(member.role()))
                .anyMatch(member -> message.mentions().contains(member.id()));
    }

    /** v0.0.29 🍊 True when the coworker already holds a ticket that the team can see. */
    private boolean hasLiveTicket(AgentProfile author) {
        return tickets.list(author.roomId()).stream()
                .anyMatch(ticket -> author.agentId().value().equals(ticket.assigneeId())
                        && LIVE.contains(ticket.status()));
    }

    /** v0.0.29 🍊 The room's active project manager (never the author itself). */
    private Optional<AgentProfile> pmOf(String roomId, AgentProfile author) {
        return agents.list(roomId).stream()
                .filter(agent -> agent.role() == Role.PROJECT_MANAGER)
                .filter(agent -> agent.state() == AgentProfile.State.ACTIVE)
                .filter(agent -> agent.scope().has(Permission.TASK_ASSIGN))
                .filter(agent -> !agent.agentId().equals(author.agentId()))
                .findFirst();
    }

    /** v0.0.29 🍊 Posts the PM's single reminder and tells the PM's mind to turn the request into a ticket. */
    private void warn(AgentProfile pm, AgentProfile author, ChatMessage message, String key) {
        if (!loopGuard.allowAgentPost(message.roomId())) {
            log.info("Ticket governor: the room budget is used up, no reminder for {}", author.name());
            return;
        }
        stages.put(key, Stage.WARNED);
        String text = "@" + author.name() + " please pause that: there is no ticket for this request yet, so the "
                + "team cannot see or review the work. I am turning it into a ticket and will assign it. Reply here "
                + "once you have stopped.";
        chat.post(new ChatPost(message.roomId(), AuthorKind.AGENT, pm.agentId().value(), pm.name(), MessageKind.TEXT,
                text, message.causalDepth() + 1, false, message.id(), null, true, StreamState.NONE,
                message.traceId(), List.of(author.agentId().value())));
        notices.notify(pm.agentId(), "the ticket governor", author.name() + " (" + author.title() + ") told "
                + quote(message.content()) + " in the group chat and started on it without a ticket. I have already "
                + "@mentioned " + author.name() + " to stop. I should create a ticket for this request and assign "
                + "it, then tell " + author.name() + " to continue.", message.traceId(), message.causalDepth() + 1);
        log.info("Ticket governor: {} reminded {} to work through a ticket", pm.name(), author.name());
    }

    /** v0.0.29 🍊 A short, quoted excerpt of untrusted chat text for the notice. */
    private static String quote(String content) {
        String clean = content.replaceAll("\\p{Cntrl}", " ").replace("\"", "'").strip();
        String shown = clean.length() > 200 ? clean.substring(0, 200).strip() + "…" : clean;
        return "\"" + shown + "\"";
    }
}
