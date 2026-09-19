package ai.yuzu.tool.impl.file;

import ai.yuzu.agent.Permission;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.llm.structured.Nullable;
import ai.yuzu.tool.spi.PermissionGuard;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.tool.spi.ToolSpec;
import ai.yuzu.workspace.AgentWorkspace;
import ai.yuzu.workspace.WorkspaceEntry;
import ai.yuzu.workspace.WorkspaceListing;
import ai.yuzu.workspace.WorkspaceService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * v0.0.27 🍊 Lists a folder of my own workspace, or describes one file (kind, size, when it changed).
 *
 * <p>The same path guard as every other workspace operation applies, so a path that leaves the workspace
 * surfaces as the {@code SANDBOX_VIOLATION} error. File names can repeat outside text, so the tool is NOT
 * trusted: its output passes the outbound safety review.</p>
 */
@Component
public class FileListTool implements Tool<FileListTool.Args> {

    /** v0.0.27 🍊 Arguments. */
    public record Args(@Nullable @Desc("Folder to list, or a file to describe; null or empty lists my whole workspace") String path) {
    }

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("file_list",
            "List a folder of my workspace (or describe one file): names, sizes and when they last changed, "
                    + "plus how much of my workspace quota is used.",
            Args.class, Set.of(Permission.FILE_READ), Risk.LOW, Duration.ofSeconds(30), false,
            "I read in my files");

    private final WorkspaceService workspaces;
    private final PermissionGuard guard;
    private final NaturalTime time;

    /** v0.0.27 🍊 Injects collaborators. */
    public FileListTool(WorkspaceService workspaces, PermissionGuard guard, NaturalTime time) {
        this.workspaces = workspaces;
        this.guard = guard;
        this.time = time;
    }

    /** v0.0.27 🍊 Static description. */
    @Override
    public ToolSpec<Args> spec() {
        return SPEC;
    }

    /** v0.0.27 🍊 Lists a folder or describes a file (permission and sandbox checks in code). */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.FILE_READ);
        String path = FileFormat.path(args.path());
        AgentWorkspace workspace = workspaces.forAgent(ctx.agent().agentId());
        WorkspaceEntry entry = workspace.stat(path);
        String body = entry.isDirectory() ? renderListing(workspace.list(path)) : describe(entry);
        return ToolResult.ok(FileFormat.cutBody(body, 200) + "\n" + quota(workspace), time.nowInstant());
    }

    /** v0.0.27 🍊 Folders first, then files, one line each. */
    private String renderListing(WorkspaceListing listing) {
        String where = listing.path().isEmpty() ? "my workspace" : listing.path();
        if (listing.entries().isEmpty()) {
            return where + " is empty.";
        }
        StringBuilder text = new StringBuilder(where + " holds " + listing.totalEntries()
                + (listing.totalEntries() == 1 ? " entry" : " entries") + ":\n");
        listing.entries().forEach(entry -> text.append("- ").append(describe(entry)).append('\n'));
        if (listing.truncated()) {
            text.append("(only the first ").append(listing.entries().size()).append(" are shown)\n");
        }
        return text.toString();
    }

    /** v0.0.27 🍊 One entry: path, kind, size and the natural-language time of its last change. */
    private String describe(WorkspaceEntry entry) {
        String kind = switch (entry.kind()) {
            case DIRECTORY -> "folder";
            case FILE -> "file, " + FileFormat.bytes(entry.sizeBytes());
            case SYMLINK -> "link (I cannot open it)";
            case OTHER -> "special file (I cannot open it)";
        };
        return entry.path() + " — " + kind + ", changed " + time.compact(entry.modifiedAt());
    }

    /** v0.0.27 🍊 How much of the workspace quota is used. */
    private static String quota(AgentWorkspace workspace) {
        return "I use " + FileFormat.bytes(workspace.usedBytes()) + " of my "
                + FileFormat.bytes(workspace.quotaBytes()) + " workspace quota.";
    }
}
