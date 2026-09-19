package ai.yuzu.external.chat;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.llm.ModelTier;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.prompt.SegmentRank;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.module.AiModule;
import ai.yuzu.module.ContextAssembler;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.module.ModuleSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * v0.0.16 🍊 Writes the yellow security notice after the inbound safety review blocked a message (DEFAULT tier).
 *
 * <p>Sees the chat window with times and speakers, the review's findings and the agent's profile.</p>
 */
@Component
public class BlockNoticeModule extends AiModule<BlockNoticeModule.Input, BlockNoticeModule.Notice> {

    /** v0.0.16 🍊 Input: the chat context, the blocked messages and the findings. */
    public record Input(String roomId, List<ChatMessage> window, List<ChatMessage> blocked, List<String> violations,
                        String reason) {
    }

    /** v0.0.16 🍊 Output: the notice text. */
    public record Notice(@Desc("Brief reasoning") String reasoning,
                         @Desc("The notice, starting with an @mention of the author of the blocked message") String text) {
    }

    private static final ModuleSpec<Notice> SPEC = new ModuleSpec<>("CHAT", "Writing a security notice",
            ModelTier.DEFAULT, "block_notice", Notice.class, false);

    private final ContextAssembler context;

    /** v0.0.16 🍊 Injects dependencies. */
    public BlockNoticeModule(ModuleDeps deps, ContextAssembler context) {
        super(deps);
        this.context = context;
    }

    /** v0.0.16 🍊 Static description. */
    @Override
    protected ModuleSpec<Notice> spec() {
        return SPEC;
    }

    /** v0.0.16 🍊 S3 self, S6 window, S7 blocked messages + findings. */
    @Override
    protected void compose(AgentContext ctx, Input input, PromptBuilder prompt) {
        prompt.add(SegmentRank.S3_SELF, context.self(ctx.profile()))
                .add(SegmentRank.S6_CHAT, "Earlier messages (context)", context.renderMessages(input.roomId(), input.window()))
                .add(SegmentRank.S7_STIMULUS, "Blocked message(s) and the safety review's findings",
                        context.renderMessages(input.roomId(), input.blocked()) + "\n\nFindings: "
                                + String.join("; ", input.violations()) + "\nSuggested reason: " + input.reason());
    }

    /** v0.0.16 🍊 The notice must not be empty. */
    @Override
    protected List<String> semanticErrors(AgentContext ctx, Input input, Notice output) {
        return output.text() == null || output.text().isBlank() ? List.of("text must not be empty.") : List.of();
    }

    /** v0.0.16 🍊 Plain fallback notice. */
    @Override
    protected Optional<Notice> degrade(AgentContext ctx, Input input, YuzuException error) {
        ChatMessage last = input.blocked().getLast();
        return Optional.of(new Notice("fallback", "@" + last.authorName()
                + " I could not process that request because it did not pass my safety review."
                + (input.reason() == null ? "" : " " + input.reason())));
    }
}
