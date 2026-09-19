package ai.yuzu.external.chat;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.chat.AuthorKind;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.llm.ModelTier;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.prompt.SegmentRank;
import ai.yuzu.module.AiModule;
import ai.yuzu.module.ContextAssembler;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.module.ModuleSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * v0.0.15 🍊 Chat triage (DEFAULT tier): does a new group message matter to this agent? IGNORE / REPLY / FORWARD.
 *
 * <p>Sees the anchored chat window (with times and speakers), the new messages, working memory, the task list,
 * its profile and a busy/idle status line. An unclosed @mention from another agent may not be ignored.</p>
 */
@Component
public class ChatModule extends AiModule<ChatModule.Input, ChatDecision> {

    /**
     * v0.0.15 🍊 Input of one triage.
     *
     * @param window    anchored context before the new messages
     * @param unread    the new messages (oldest first)
     * @param taskState rendered current task list
     * @param status    busy/idle status line
     */
    public record Input(String roomId, List<ChatMessage> window, List<ChatMessage> unread, String taskState,
                        String status) {

        /** v0.0.15 🍊 The most recent new message. */
        public ChatMessage newest() {
            return unread.getLast();
        }
    }

    private static final ModuleSpec<ChatDecision> SPEC = new ModuleSpec<>("CHAT", "Reading the group chat",
            ModelTier.DEFAULT, "chat", ChatDecision.class, false);

    private final ContextAssembler context;

    /** v0.0.15 🍊 Injects dependencies. */
    public ChatModule(ModuleDeps deps, ContextAssembler context) {
        super(deps);
        this.context = context;
    }

    /** v0.0.15 🍊 Static description. */
    @Override
    protected ModuleSpec<ChatDecision> spec() {
        return SPEC;
    }

    /** v0.0.15 🍊 S2 roster, S3 self, S4 tasks, S5 working memory, S6 window, S7 new messages + status. */
    @Override
    protected void compose(AgentContext ctx, Input input, PromptBuilder prompt) {
        prompt.add(SegmentRank.S2_ROSTER, context.roster(input.roomId()))
                .add(SegmentRank.S3_SELF, context.self(ctx.profile()))
                .add(SegmentRank.S4_SLOW_STATE, "Your task list", input.taskState())
                .add(SegmentRank.S5_WORKING_MEMORY, context.workingMemory(ctx.agentId()))
                .add(SegmentRank.S6_CHAT, "Earlier messages (context)",
                        context.renderMessages(input.roomId(), input.window()))
                .add(SegmentRank.S7_STIMULUS, "New message(s) to triage",
                        context.renderMessages(input.roomId(), input.unread()) + "\n\n" + input.status());
    }

    /** v0.0.15 🍊 Monitor text. */
    @Override
    protected String startText(Input input) {
        return "Reading a message from " + input.newest().authorName();
    }

    /** v0.0.15 🍊 Monitor text. */
    @Override
    protected String endText(ChatDecision output) {
        return switch (output.decision()) {
            case IGNORE -> "Not for me";
            case REPLY -> "Replying";
            case FORWARD -> "Thinking about it";
        };
    }

    /** v0.0.15 🍊 REPLY needs text; an unclosed @mention from an agent cannot be ignored. */
    @Override
    protected List<String> semanticErrors(AgentContext ctx, Input input, ChatDecision output) {
        List<String> errors = new ArrayList<>();
        if (output.decision() == ChatDecision.Decision.REPLY
                && (output.replyText() == null || output.replyText().isBlank())) {
            errors.add("decision is REPLY but replyText is empty: write the reply or choose FORWARD.");
        }
        if (output.decision() == ChatDecision.Decision.IGNORE && mentionedByAgentUnclosed(ctx, input)) {
            errors.add("An AI coworker @mentioned you and the topic is not closed: choose REPLY (simple) or FORWARD.");
        }
        return errors;
    }

    /** v0.0.15 🍊 When triage keeps failing: forward agent mentions, ignore everything else. */
    @Override
    protected Optional<ChatDecision> degrade(AgentContext ctx, Input input, YuzuException error) {
        boolean mustAnswer = mentionedByAgentUnclosed(ctx, input) || input.unread().stream()
                .anyMatch(m -> m.authorKind() == AuthorKind.HUMAN && m.mentions(ctx.agentId().value()));
        return Optional.of(new ChatDecision("Chat triage unavailable: " + error.code(),
                mustAnswer ? ChatDecision.Decision.FORWARD : ChatDecision.Decision.IGNORE, null, null, false));
    }

    /** v0.0.15 🍊 True when a new message from another agent @mentions this agent and is not a closure. */
    static boolean mentionedByAgentUnclosed(AgentContext ctx, Input input) {
        return input.unread().stream().anyMatch(m -> m.authorKind() == AuthorKind.AGENT && !m.closure()
                && m.mentions().contains(ctx.agentId().value()));
    }
}
