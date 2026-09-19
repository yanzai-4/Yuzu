package ai.yuzu.tool.impl.code;

import ai.yuzu.agent.Permission;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.structured.Desc;
import ai.yuzu.tool.spi.PermissionGuard;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.tool.spi.ToolSpec;
import ai.yuzu.workspace.AgentWorkspace;
import ai.yuzu.workspace.WorkspaceArea;
import ai.yuzu.workspace.WorkspaceEntry;
import ai.yuzu.workspace.WorkspaceService;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * v0.0.26 🍊 Writes a code file into the agent's {@code code/} area and, on request, runs it offline.
 *
 * <p>Writing needs {@code CODE_WRITE}; running additionally needs {@code CODE_EXECUTE}, both re-checked here
 * (defense in depth). Execution happens inside macOS {@code sandbox-exec}: no network, writes only inside the
 * workspace, a wall-clock limit, a cleaned environment and truncated output. When {@code sandbox-exec} is
 * missing, the file is still written but nothing runs, and the result says so.</p>
 *
 * <p>The result text is produced entirely by our own code (paths, exit codes, the script's own output), so the
 * tool is trusted per plan section 8: it skips the AI outbound review but still passes the code checks.</p>
 */
@Component
public class CodeWriteTool implements Tool<CodeWriteTool.Args> {

    /** v0.0.26 🍊 Arguments. */
    public record Args(@Desc("File name inside my code/ folder, e.g. 'analysis.py' or 'tools/report.py'") String path,
                       @Desc("The complete file content") String content,
                       @Desc("Whether to run the file right away in the offline sandbox") boolean run) {
    }

    /** v0.0.26 🍊 Wall-clock budget of one sandboxed run. */
    static final Duration RUN_TIMEOUT = Duration.ofSeconds(20);

    private static final ToolSpec<Args> SPEC = new ToolSpec<>("code_write",
            "Write a code file into my code/ folder and optionally run it right away in an offline sandbox "
                    + "(no network, only my workspace is writable, 20 seconds). Runnable file types: "
                    + ScriptRuntime.runnableExtensions() + ".",
            Args.class, Set.of(Permission.CODE_WRITE), Risk.HIGH, Duration.ofSeconds(60), true,
            "I wrote and ran this code myself");

    private final WorkspaceService workspaces;
    private final SandboxRunner sandbox;
    private final PermissionGuard guard;
    private final NaturalTime time;

    /** v0.0.26 🍊 Injects collaborators. */
    public CodeWriteTool(WorkspaceService workspaces, SandboxRunner sandbox, PermissionGuard guard, NaturalTime time) {
        this.workspaces = workspaces;
        this.sandbox = sandbox;
        this.guard = guard;
        this.time = time;
    }

    /** v0.0.26 🍊 Static description. */
    @Override
    public ToolSpec<Args> spec() {
        return SPEC;
    }

    /** v0.0.26 🍊 Writes the file, then runs it in the sandbox when asked and allowed. */
    @Override
    public ToolResult execute(ToolContext ctx, Args args) {
        guard.require(ctx, Permission.CODE_WRITE);
        if (args.run()) {
            guard.require(ctx, Permission.CODE_EXECUTE);
        }
        String name = args.path() == null ? "" : args.path().strip();
        if (name.isEmpty()) {
            return ToolResult.error("I need a file name for the code.", time.nowInstant());
        }
        String content = args.content() == null ? "" : args.content();
        try {
            AgentWorkspace workspace = workspaces.forAgent(ctx.agent().agentId());
            String path = inCodeArea(name);
            ctx.span().state("writing " + path);
            WorkspaceEntry written = workspace.writeText(path, content);
            StringBuilder sb = new StringBuilder("I wrote ").append(written.sizeBytes()).append(" bytes to ")
                    .append(path).append(" in my workspace.\n");
            sb.append(args.run() ? runReport(ctx, workspace, path) : "I did not run it, because I only wrote it.");
            return ToolResult.ok(sb.toString().strip(), time.nowInstant());
        } catch (YuzuException e) {
            return ToolResult.error(e.getMessage(), time.nowInstant());
        }
    }

    /** v0.0.26 🍊 Runs the file if its type is runnable and the sandbox exists; renders what happened. */
    private String runReport(ToolContext ctx, AgentWorkspace workspace, String path) {
        Optional<ScriptRuntime> runtime = ScriptRuntime.forFile(path);
        if (runtime.isEmpty()) {
            return "I did not run it: I can only run " + ScriptRuntime.runnableExtensions() + " files.";
        }
        Optional<String> blocked = sandbox.unavailableReason(runtime.get());
        if (blocked.isPresent()) {
            return "I did not run it: " + blocked.get() + ".";
        }
        ctx.span().state("running " + path + " in the sandbox");
        Path script = workspace.resolve(path);
        RunOutcome outcome = sandbox.run(workspace.root(), script, runtime.get(), RUN_TIMEOUT);
        if (!outcome.ran()) {
            return "I did not run it: " + outcome.reason() + ".";
        }
        StringBuilder sb = new StringBuilder("I ran it with " + runtime.get().display()
                + " in the offline sandbox (no network, only my workspace writable): ");
        if (outcome.timedOut()) {
            sb.append("it was still running after ").append(RUN_TIMEOUT.toSeconds())
                    .append(" seconds, so the sandbox stopped it.");
        } else {
            sb.append("exit code ").append(outcome.exitCode()).append(" after ")
                    .append(String.format("%.1f", outcome.took().toMillis() / 1000.0)).append(" seconds.");
        }
        sb.append('\n').append(stream("Output", outcome.stdout()));
        sb.append(stream("Errors", outcome.stderr()));
        if (outcome.truncated()) {
            sb.append("(the script printed more than I keep, so this output is cut)\n");
        }
        return sb.toString();
    }

    /** v0.0.26 🍊 One captured stream as a labelled block ("(none)" when empty). */
    private static String stream(String label, String text) {
        return label + ":\n" + (text == null || text.isBlank() ? "(none)" : text) + "\n";
    }

    /** v0.0.26 🍊 The path inside code/ (a leading "code/" the model wrote is not doubled). */
    static String inCodeArea(String name) {
        String cleaned = name.replace('\\', '/').strip();
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        String prefix = WorkspaceArea.CODE.dir() + "/";
        return cleaned.startsWith(prefix) ? cleaned : WorkspaceArea.CODE.path(cleaned);
    }
}
