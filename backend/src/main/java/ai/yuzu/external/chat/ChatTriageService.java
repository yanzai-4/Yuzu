package ai.yuzu.external.chat;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntime;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.chat.AuthorKind;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.chat.ChatPost;
import ai.yuzu.chat.ChatService;
import ai.yuzu.chat.RoomWindow;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.module.TaskStateProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * v0.0.15 🍊 Applies chat triage for an agent: runs the chat module and carries out IGNORE / REPLY / FORWARD.
 *
 * <p>REPLY: code makes sure the reply @mentions the person answered, sets the closure flag and the causal depth,
 * and respects the loop guard. FORWARD: posts the acknowledgement for human work requests, then hands the
 * window and the new messages to the internal modules through the {@link ChatForwarder}.</p>
 */
@Service
public class ChatTriageService implements ChatInbox.Handler {

    private static final Logger log = LoggerFactory.getLogger(ChatTriageService.class);

    private final ChatModule module;
    private final AgentRuntimeManager runtimes;
    private final ChatService chat;
    private final RoomWindow window;
    private final LoopGuard loopGuard;
    private final NaturalTime time;
    private final ObjectProvider<TaskStateProvider> tasks;
    private final ObjectProvider<ChatForwarder> forwarder;

    /** v0.0.15 🍊 Injects collaborators (task state and forwarder are optional, resolved lazily). */
    public ChatTriageService(ChatModule module, AgentRuntimeManager runtimes, ChatService chat, RoomWindow window,
                             LoopGuard loopGuard, NaturalTime time, ObjectProvider<TaskStateProvider> tasks,
                             ObjectProvider<ChatForwarder> forwarder) {
        this.module = module;
        this.runtimes = runtimes;
        this.chat = chat;
        this.window = window;
        this.loopGuard = loopGuard;
        this.time = time;
        this.tasks = tasks;
        this.forwarder = forwarder;
    }

    /** v0.0.15 🍊 Triage of a batch of new messages for one agent. */
    @Override
    public void handle(AgentId agentId, List<ChatMessage> batch) {
        AgentRuntime runtime = runtimes.find(agentId).orElse(null);
        if (runtime == null || runtime.isPaused() || batch.isEmpty()) {
            return;
        }
        AgentContext ctx = runtime.context(null, null, time);
        String roomId = batch.getLast().roomId();
        List<ChatMessage> context = window.anchoredContext(roomId, batch.getFirst());
        String taskState = tasks.getIfAvailable(() -> TaskStateProvider.NONE).current(agentId);
        ChatModule.Input input = new ChatModule.Input(roomId, context, batch, taskState, status(runtime));
        ChatDecision decision = module.run(ctx, input);
        switch (decision.decision()) {
            case IGNORE -> {
            }
            case REPLY -> reply(ctx, runtime, input.newest(), decision);
            case FORWARD -> forward(ctx, runtime, new ChatForward(roomId, context, batch, input.newest()), decision);
        }
    }

    /** v0.0.15 🍊 Posts a direct reply (mention enforced, closure and depth set, loop guard respected). */
    private void reply(AgentContext ctx, AgentRuntime runtime, ChatMessage trigger, ChatDecision decision) {
        if (!loopGuard.allowReply(trigger, ctx.agentId().value())) {
            log.info("Loop guard dropped a reply of {} to {}", ctx.agentId(), trigger.id());
            return;
        }
        String text = ensureMention(decision.replyText(), trigger);
        chat.post(ChatPost.agent(trigger.roomId(), ctx.agentId().value(), runtime.profile().name(), text,
                trigger.causalDepth() + 1, decision.topicClosed(), ctx.traceId()).replyingTo(trigger.id()));
    }

    /** v0.0.15 🍊 Posts the acknowledgement for a human request, then forwards to the internal modules. */
    private void forward(AgentContext ctx, AgentRuntime runtime, ChatForward forward, ChatDecision decision) {
        ChatMessage newest = forward.newest();
        if (decision.ackText() != null && !decision.ackText().isBlank() && newest.authorKind() == AuthorKind.HUMAN
                && loopGuard.allowAgentPost(newest.roomId())) {
            chat.post(ChatPost.agent(newest.roomId(), ctx.agentId().value(), runtime.profile().name(),
                    ensureMention(decision.ackText(), newest), newest.causalDepth() + 1, true, ctx.traceId())
                    .replyingTo(newest.id()));
        }
        ChatForwarder target = forwarder.getIfAvailable();
        if (target == null) {
            log.warn("No chat forwarder registered; {} cannot think about {}", ctx.agentId(), newest.id());
            return;
        }
        target.forward(ctx, forward);
    }

    /** v0.0.15 🍊 Prefixes "@Name " when the text does not already mention the person answered. */
    static String ensureMention(String text, ChatMessage trigger) {
        String body = text == null ? "" : text.strip();
        String tag = "@" + trigger.authorName();
        if (trigger.authorKind() == AuthorKind.SYSTEM
                || body.toLowerCase(Locale.ROOT).contains(tag.toLowerCase(Locale.ROOT))) {
            return body;
        }
        return tag + " " + body;
    }

    /** v0.0.15 🍊 Busy/idle status line shown to the chat module. */
    private static String status(AgentRuntime runtime) {
        int waiting = runtime.consciousness().pool().size();
        boolean thinking = runtime.consciousness().pool().isRunning();
        return thinking || waiting > 0
                ? "Your status: BUSY (thinking; " + waiting + " message(s) waiting in your mind)."
                : "Your status: available.";
    }
}
