package ai.yuzu.internal.intake;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntime;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.chat.ChatPost;
import ai.yuzu.chat.ChatService;
import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.external.chat.BlockNoticeModule;
import ai.yuzu.external.chat.ChatForward;
import ai.yuzu.external.chat.ChatForwarder;
import ai.yuzu.external.safety.SafetyService;
import ai.yuzu.internal.cognition.HabitAdvisor;
import ai.yuzu.internal.consciousness.Origin;
import ai.yuzu.internal.planning.TaskPlanner;
import ai.yuzu.module.ContextAssembler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * v0.0.16 🍊 The single path from the outside world into an agent's mind.
 *
 * <p>Chat forwards: inbound safety gate (blocked → yellow notice by the chat module) → planning ∥ cognition
 * (run in parallel; they only see external information) → one EXTERNAL pool message with a first-person
 * attribution (which also schedules a subconscious pass and wakes the main loop). Tool results and question
 * answers join at the planning/cognition step (their safety review happens where they are produced).</p>
 */
@Service
public class IntakePipeline implements ChatForwarder {

    private final AgentRuntimeManager runtimes;
    private final SafetyService safety;
    private final BlockNoticeModule blockNotice;
    private final ContextAssembler context;
    private final ChatService chat;
    private final AsyncRunner runner;
    private final NaturalTime time;
    private final ObjectProvider<TaskPlanner> planner;
    private final ObjectProvider<HabitAdvisor> habits;

    /** v0.0.16 🍊 Injects collaborators (planner and habit advisor are optional). */
    public IntakePipeline(AgentRuntimeManager runtimes, SafetyService safety, BlockNoticeModule blockNotice,
                          ContextAssembler context, ChatService chat, AsyncRunner runner, NaturalTime time,
                          ObjectProvider<TaskPlanner> planner, ObjectProvider<HabitAdvisor> habits) {
        this.runtimes = runtimes;
        this.safety = safety;
        this.blockNotice = blockNotice;
        this.context = context;
        this.chat = chat;
        this.runner = runner;
        this.time = time;
        this.planner = planner;
        this.habits = habits;
    }

    /** v0.0.16 🍊 Chat forwarded by the chat module. */
    @Override
    public void forward(AgentContext ctx, ChatForward forward) {
        AgentRuntime runtime = runtimes.require(ctx.agentId());
        String newText = context.renderMessages(forward.roomId(), forward.unread());
        SafetyService.GateResult gate = safety.gate(ctx, newText, "group chat");
        ChatMessage newest = forward.newest();
        if (!gate.allowed()) {
            BlockNoticeModule.Notice notice = blockNotice.run(ctx, new BlockNoticeModule.Input(forward.roomId(),
                    forward.window(), forward.unread(), gate.violations(), gate.userFacingReason()));
            chat.post(ChatPost.warning(forward.roomId(), ctx.agentId().value(), runtime.profile().name(),
                    mention(notice.text(), newest), ctx.traceId()));
            return;
        }
        ChatDeliveryTracker tracker = runtime.component(ChatDeliveryTracker.class);
        long firstUnread = forward.unread().getFirst().seq();
        List<ChatMessage> earlier = new ArrayList<>();
        for (ChatMessage m : forward.window()) {
            if (m.seq() > tracker.lastDeliveredSeq() && m.seq() < firstUnread) {
                earlier.add(m);
            }
        }
        tracker.delivered(newest.seq());
        Stimulus.ChatStimulus stimulus = new Stimulus.ChatStimulus(forward.roomId(), earlier, forward.unread());
        String text = (earlier.isEmpty() ? "" : "Chat I had not seen yet:\n"
                + context.renderMessages(forward.roomId(), earlier) + "\n\n")
                + "New messages:\n" + newText;
        String attribution = "the group chat (latest: " + context.speaker(forward.roomId(), newest) + " at "
                + time.compact(newest.createdAt()) + ")";
        deliver(ctx, runtime, stimulus, text, attribution, newest.causalDepth());
    }

    /** v0.0.16 🍊 Delivers already-reviewed tool results, question answers or notices into the mind. */
    public void deliver(AgentContext ctx, Stimulus stimulus, String text, String attribution, int causalDepth) {
        deliver(ctx, runtimes.require(ctx.agentId()), stimulus, text, attribution, causalDepth);
    }

    /** v0.0.16 🍊 Planning ∥ cognition, then one EXTERNAL pool message. */
    private void deliver(AgentContext ctx, AgentRuntime runtime, Stimulus stimulus, String text, String attribution,
                         int causalDepth) {
        HabitAdvisor advisor = habits.getIfAvailable();
        CompletableFuture<String> habitNote = advisor == null ? CompletableFuture.completedFuture(null)
                : runner.supply("cognition", ctx.agentId().value(), () -> advisor.advise(ctx, stimulus, text));
        TaskPlanner taskPlanner = planner.getIfAvailable();
        String taskNote = taskPlanner == null ? null : taskPlanner.plan(ctx, stimulus, text);
        String habits = habitNote.exceptionally(e -> null).join();
        StringBuilder poolText = new StringBuilder(text);
        if (habits != null && !habits.isBlank()) {
            poolText.append("\n\n").append(habits.strip());
        }
        if (taskNote != null && !taskNote.isBlank()) {
            poolText.append("\n\n").append(taskNote.strip());
        }
        runtime.consciousness().offer(Origin.EXTERNAL, attribution, poolText.toString(), ctx.traceId(), causalDepth,
                false);
    }

    /** v0.0.16 🍊 Ensures the notice @mentions the author of the blocked message. */
    private static String mention(String text, ChatMessage newest) {
        String tag = "@" + newest.authorName();
        return text.toLowerCase(Locale.ROOT).contains(tag.toLowerCase(Locale.ROOT)) ? text : tag + " " + text;
    }
}
