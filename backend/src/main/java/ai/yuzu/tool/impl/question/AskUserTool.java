package ai.yuzu.tool.impl.question;

import ai.yuzu.agent.Permission;
import ai.yuzu.card.CardService;
import ai.yuzu.card.CardView;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.tool.spi.PermissionGuard;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.tool.spi.ToolSpec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * v0.0.19 🍊 Asks the humans a multiple-choice question with a card. Non-blocking (confirmed): the call returns
 * WAITING at once and the answer arrives later as new input, without passing through any chat module.
 */
@Component
public class AskUserTool implements Tool<AskUserTool.Args> {

    /** v0.0.19 🍊 Arguments. */
    public record Args(@Desc("The question; you may start with an @mention of the human you ask") String question,
                       @Desc("2 to 6 options") List<String> options,
                       @Desc("Whether a free-text 'Other' answer is allowed") boolean allowOther) {
    }

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("ask_user",
            "Ask the humans a multiple-choice question with a card (optionally allowing a free-text answer).",
            Args.class, Set.of(Permission.ASK_USER), Risk.LOW, Duration.ofSeconds(15), true,
            "the card system confirmed");

    private final CardService cards;
    private final PermissionGuard guard;
    private final NaturalTime time;

    /** v0.0.19 🍊 Injects collaborators. */
    public AskUserTool(CardService cards, PermissionGuard guard, NaturalTime time) {
        this.cards = cards;
        this.guard = guard;
        this.time = time;
    }

    /** v0.0.19 🍊 Static description. */
    @Override
    public ToolSpec<Args> spec() {
        return SPEC;
    }

    /** v0.0.19 🍊 Opens the card and returns WAITING. */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.ASK_USER);
        List<String> options = args.options() == null ? List.of() : args.options().stream()
                .filter(o -> o != null && !o.isBlank()).toList();
        if (options.size() < 2 || options.size() > 6) {
            return ToolResult.error("A question card needs 2 to 6 options.", time.nowInstant());
        }
        CardView card = cards.open(ctx.agent(), false, args.question(), options, args.allowOther(),
                CardService.QUESTION, null, ctx.batchId(), ctx.toolCallId());
        return ToolResult.waiting("I asked the humans with a question card (" + card.id()
                + "); their answer will reach me when someone replies.", time.nowInstant());
    }
}
