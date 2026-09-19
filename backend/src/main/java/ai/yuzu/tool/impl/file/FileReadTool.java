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
import ai.yuzu.workspace.LineSlice;
import ai.yuzu.workspace.TextChunk;
import ai.yuzu.workspace.WorkspaceService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * v0.0.27 🍊 Reads a text file of my own workspace, in continuable pieces so a file of any size can be worked
 * through.
 *
 * <p>Two ways to read, both streaming inside {@link AgentWorkspace}: a numbered line range
 * ({@code fromLine}/{@code toLine}) or a byte chunk ({@code offsetBytes}, cut only at UTF-8 character
 * boundaries). Every result says how to continue. File content can carry outside text, so the tool is NOT
 * trusted: its output passes the outbound safety review.</p>
 */
@Component
public class FileReadTool implements Tool<FileReadTool.Args> {

    /** v0.0.27 🍊 Arguments. */
    public record Args(@Desc("Path inside my workspace, for example files/notes.md") String path,
                       @Nullable @Desc("First line to read (1-based) when I want a line range; null for a byte chunk") Long fromLine,
                       @Nullable @Desc("Last line to read (inclusive); null reads a page of lines from fromLine") Long toLine,
                       @Nullable @Desc("Byte offset to continue a chunked read from (the previous result says which); null starts at the beginning") Long offsetBytes) {
    }

    /** v0.0.27 🍊 Bytes returned by one chunk read when the agent does not ask for lines. */
    static final int CHUNK_BYTES = 16 << 10;

    /** v0.0.27 🍊 Lines returned when only fromLine is given. */
    static final long LINE_PAGE = 200;

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("file_read",
            "Read a text file in my workspace. Leave the line numbers empty to read the file in continuable "
                    + "chunks (large files are read piece by piece), or give fromLine/toLine for a numbered line "
                    + "range.",
            Args.class, Set.of(Permission.FILE_READ), Risk.LOW, Duration.ofSeconds(60), false,
            "I read in my files");

    private final WorkspaceService workspaces;
    private final PermissionGuard guard;
    private final NaturalTime time;

    /** v0.0.27 🍊 Injects collaborators. */
    public FileReadTool(WorkspaceService workspaces, PermissionGuard guard, NaturalTime time) {
        this.workspaces = workspaces;
        this.guard = guard;
        this.time = time;
    }

    /** v0.0.27 🍊 Static description. */
    @Override
    public ToolSpec<Args> spec() {
        return SPEC;
    }

    /** v0.0.27 🍊 Reads a line range or a byte chunk (permission and sandbox checks in code). */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.FILE_READ);
        String path = FileFormat.path(args.path());
        if (path.isEmpty()) {
            return ToolResult.error("A file path is required, for example files/notes.md.", time.nowInstant());
        }
        AgentWorkspace workspace = workspaces.forAgent(ctx.agent().agentId());
        return args.fromLine() != null
                ? ToolResult.ok(renderLines(workspace, path, args), time.nowInstant())
                : ToolResult.ok(renderChunk(workspace, path, args), time.nowInstant());
    }

    /** v0.0.27 🍊 A numbered line range with a note about the lines that follow. */
    private String renderLines(AgentWorkspace workspace, String path, Args args) {
        long from = Math.max(1, args.fromLine());
        long to = args.toLine() == null ? from + LINE_PAGE - 1 : Math.max(from, args.toLine());
        LineSlice slice = workspace.readLines(path, from, to);
        if (slice.isEmpty()) {
            return path + " has no lines from line " + from + " on.";
        }
        StringBuilder text = new StringBuilder(path + ", lines " + slice.fromLine() + " to " + slice.toLine() + ":\n");
        text.append(FileFormat.cutBody(slice.numbered(), 300));
        if (slice.truncated()) {
            text.append("(some lines were cut to fit the size budget)\n");
        }
        if (slice.hasMore()) {
            text.append("More lines follow; read again from fromLine = ").append(slice.toLine() + 1).append(".");
        } else {
            text.append("That is the end of the file.");
        }
        return text.toString();
    }

    /** v0.0.27 🍊 One byte chunk with the offset the next call continues from. */
    private String renderChunk(AgentWorkspace workspace, String path, Args args) {
        long offset = args.offsetBytes() == null ? 0 : Math.max(0, args.offsetBytes());
        TextChunk chunk = workspace.readChunk(path, offset, CHUNK_BYTES);
        StringBuilder text = new StringBuilder(path + " (" + FileFormat.bytes(chunk.fileSizeBytes()) + ", read from "
                + "byte " + chunk.offsetBytes() + "):\n");
        if (chunk.binary()) {
            text.append("(this file does not look like text; the bytes below may be unreadable)\n");
        }
        text.append(FileFormat.cutBody(chunk.text(), 300));
        text.append('\n').append(chunk.eof()
                ? "That is the end of the file."
                : "The file continues; read it again with offsetBytes = " + chunk.nextOffsetBytes() + ".");
        return text.toString();
    }
}
