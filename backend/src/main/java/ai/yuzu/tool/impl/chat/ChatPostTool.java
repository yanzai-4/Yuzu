package ai.yuzu.tool.impl.chat;

import ai.yuzu.agent.Permission;
import ai.yuzu.chat.ChatMessage;
import ai.yuzu.chat.ChatPost;
import ai.yuzu.chat.ChatService;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.external.chat.LoopGuard;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.tool.spi.PermissionGuard;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.tool.spi.ToolSpec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * v0.0.18 🍊 Posts a message in the group chat on behalf of the agent. Addressing someone requires an @mention.
 */
@Component
public class ChatPostTool implements Tool<ChatPostTool.Args> {

    /** v0.0.18 🍊 Arguments. */
    public record Args(@Desc("The complete message; it must @mention whoever it addresses (or @all)") String text) {
    }

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("chat_post",
            "Post a message in the group chat (to talk to someone, @mention them; @all for everyone).",
            Args.class, Set.of(Permission.CHAT_POST), Risk.LOW, Duration.ofSeconds(15), true,
            "the chat confirmed");

    private final ChatService chat;
    private final PermissionGuard guard;
    private final LoopGuard loopGuard;
    private final NaturalTime time;

    /** v0.0.18 🍊 Injects collaborators. */
    public ChatPostTool(ChatService chat, PermissionGuard guard, LoopGuard loopGuard, NaturalTime time) {
        this.chat = chat;
        this.guard = guard;
        this.loopGuard = loopGuard;
        this.time = time;
    }

    /** v0.0.18 🍊 Static description. */
    @Override
    public ToolSpec<Args> spec() {
        return SPEC;
    }

    /** v0.0.18 🍊 Posts the message (permission, @mention and room-budget checks in code). */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.CHAT_POST);
        String text = args.text() == null ? "" : args.text().strip();
        if (text.isEmpty()) {
            return ToolResult.error("The message is empty.", time.nowInstant());
        }
        if (text.toLowerCase(Locale.ROOT).contains("@all")) {
            guard.require(ctx, Permission.CHAT_MENTION_ALL);
        }
        if (!text.contains("@")) {
            return ToolResult.error("Chat messages must @mention the person they address (or @all).", time.nowInstant());
        }
        String roomId = ctx.profile().roomId();
        if (!loopGuard.allowAgentPost(roomId)) {
            return ToolResult.error("The room is very busy right now; try again in a minute.", time.nowInstant());
        }
        ChatMessage posted = chat.post(ChatPost.agent(roomId, ctx.agent().agentId().value(), ctx.profile().name(), text,
                ctx.causalDepth() + 1, false, ctx.agent().traceId()));
        return ToolResult.ok("Posted to the group chat at " + time.compact(posted.createdAt()) + ": \"" + text + "\"",
                posted.createdAt());
    }
}
