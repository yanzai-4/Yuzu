package ai.yuzu.tool.impl.file;

import ai.yuzu.agent.Permission;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.tool.spi.PermissionGuard;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.tool.spi.ToolSpec;
import ai.yuzu.workspace.AgentWorkspace;
import ai.yuzu.workspace.WorkspaceEntry;
import ai.yuzu.workspace.WorkspaceService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * v0.0.27 🍊 Writes a text file in my own workspace ({@code files/}, {@code code/}, {@code web/}, {@code memory/}).
 *
 * <p>Every path rule, the quota and the atomic replace live in {@link AgentWorkspace}; the tool only asks for
 * them. A path that tries to leave the workspace surfaces as the {@code SANDBOX_VIOLATION} error, never as a
 * normal failure.</p>
 */
@Component
public class FileWriteTool implements Tool<FileWriteTool.Args> {

    /** v0.0.27 🍊 Arguments. */
    public record Args(@Desc("Path inside my workspace, for example files/notes.md (no leading / and no '..')") String path,
                       @Desc("The complete text to store (at most 1 MB per call)") String content,
                       @Desc("true adds the text to the end of the file, false creates or replaces the file") boolean append) {
    }

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("file_write",
            "Write a text file in my workspace (files/, code/, web/ or memory/), creating, replacing or appending "
                    + "to it. At most 1 MB per call and within my workspace quota.",
            Args.class, Set.of(Permission.FILE_WRITE), Risk.MEDIUM, Duration.ofSeconds(30), true,
            "my workspace confirmed");

    private final WorkspaceService workspaces;
    private final PermissionGuard guard;
    private final NaturalTime time;

    /** v0.0.27 🍊 Injects collaborators. */
    public FileWriteTool(WorkspaceService workspaces, PermissionGuard guard, NaturalTime time) {
        this.workspaces = workspaces;
        this.guard = guard;
        this.time = time;
    }

    /** v0.0.27 🍊 Static description. */
    @Override
    public ToolSpec<Args> spec() {
        return SPEC;
    }

    /** v0.0.27 🍊 Writes or appends through the workspace (permission, sandbox and quota checks in code). */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.FILE_WRITE);
        String path = FileFormat.path(args.path());
        if (path.isEmpty()) {
            return ToolResult.error("A file path is required, for example files/notes.md.", time.nowInstant());
        }
        String content = args.content() == null ? "" : args.content();
        AgentWorkspace workspace = workspaces.forAgent(ctx.agent().agentId());
        WorkspaceEntry entry = args.append() ? workspace.append(path, content) : workspace.writeText(path, content);
        long free = Math.max(0, workspace.quotaBytes() - workspace.usedBytes());
        return ToolResult.ok((args.append() ? "Appended " : "Wrote ")
                + FileFormat.bytes(content.getBytes(StandardCharsets.UTF_8).length) + " to " + entry.path()
                + "; the file is now " + FileFormat.bytes(entry.sizeBytes()) + ". " + FileFormat.bytes(free)
                + " of my workspace quota is still free.", time.nowInstant());
    }
}
