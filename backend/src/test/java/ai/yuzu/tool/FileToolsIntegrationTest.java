package ai.yuzu.tool;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.common.error.ErrorCode;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.error.SandboxViolationException;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.TestRooms;
import ai.yuzu.tool.impl.file.FileListTool;
import ai.yuzu.tool.impl.file.FileReadTool;
import ai.yuzu.tool.impl.file.FileWriteTool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** v0.0.27 🍊 Workspace file tools: sandboxed writes, chunked and line reads, listings and code-level permissions. */
@IntegrationTest
class FileToolsIntegrationTest {

    @Autowired
    private AgentService agents;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private FileWriteTool writeTool;
    @Autowired
    private FileReadTool readTool;
    @Autowired
    private FileListTool listTool;
    @Autowired
    private AgentRuntimeManager runtimes;
    @Autowired
    private ModuleDeps deps;
    @Autowired
    private NaturalTime time;

    private String roomId;
    private AgentProfile researcher;
    private AgentProfile liaison;

    /** v0.0.27 🍊 One isolated room with a researcher (read + write) and a liaison (neither). */
    @BeforeEach
    void setUp() {
        roomId = TestRooms.create(jdbc);
        researcher = agents.createNamed(roomId,
                new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null), "Lime");
        liaison = agents.createNamed(roomId,
                new CreateAgentRequest(Role.CUSTOMER_LIAISON, null, null, null, null, null), "Pomelo");
    }

    /** v0.0.27 🍊 file_write creates and appends, file_read returns the text and file_list shows the file. */
    @Test
    void writesAppendsReadsAndListsWorkspaceFiles() {
        ToolResult written = writeTool.execute(context(researcher),
                new FileWriteTool.Args("files/notes.md", "# Citrus notes\nFirst finding.\n", false));
        assertThat(written.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(written.output()).contains("files/notes.md");

        ToolResult appended = writeTool.execute(context(researcher),
                new FileWriteTool.Args("files/notes.md", "Second finding.\n", true));
        assertThat(appended.status()).isEqualTo(ToolResult.Status.OK);

        ToolResult read = readTool.execute(context(researcher),
                new FileReadTool.Args("files/notes.md", null, null, null));
        assertThat(read.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(read.output()).contains("# Citrus notes").contains("First finding.").contains("Second finding.");

        ToolResult listing = listTool.execute(context(researcher), new FileListTool.Args("files"));
        assertThat(listing.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(listing.output()).contains("notes.md");

        ToolResult stat = listTool.execute(context(researcher), new FileListTool.Args("files/notes.md"));
        assertThat(stat.output()).contains("files/notes.md").contains("file");
    }

    /** v0.0.27 🍊 A large file comes back in continuable byte chunks and as numbered line ranges. */
    @Test
    void readsLargeFileInChunksAndByLines() {
        StringBuilder big = new StringBuilder();
        for (int line = 1; line <= 4_000; line++) {
            big.append("line ").append(line).append(" of the citrus market research corpus\n");
        }
        writeTool.execute(context(researcher), new FileWriteTool.Args("files/corpus.txt", big.toString(), false));

        ToolResult first = readTool.execute(context(researcher),
                new FileReadTool.Args("files/corpus.txt", null, null, null));
        assertThat(first.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(first.output()).contains("line 1 of the citrus market research corpus");
        Matcher next = Pattern.compile("offsetBytes\\s*=?\\s*(\\d+)").matcher(first.output());
        assertThat(next.find()).as("the first chunk must say where to continue").isTrue();
        long offset = Long.parseLong(next.group(1));
        assertThat(offset).isGreaterThan(0);

        ToolResult second = readTool.execute(context(researcher),
                new FileReadTool.Args("files/corpus.txt", null, null, offset));
        assertThat(second.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(second.output()).doesNotContain("line 1 of the citrus");

        ToolResult lines = readTool.execute(context(researcher),
                new FileReadTool.Args("files/corpus.txt", 3_998L, 4_000L, null));
        assertThat(lines.output()).contains("3998| line 3998").contains("4000| line 4000")
                .doesNotContain("3997| line 3997");
    }

    /** v0.0.27 🍊 Leaving the workspace with '..' surfaces as the SANDBOX_VIOLATION error, on reads and on writes. */
    @Test
    void sandboxEscapeSurfacesAsSandboxViolation() {
        SandboxViolationException onRead = catchThrowableOfType(SandboxViolationException.class,
                () -> readTool.execute(context(researcher),
                        new FileReadTool.Args("../../etc/passwd", null, null, null)));
        assertThat(onRead).isNotNull();
        assertThat(onRead.code()).isEqualTo(ErrorCode.SANDBOX_VIOLATION);

        SandboxViolationException onWrite = catchThrowableOfType(SandboxViolationException.class,
                () -> writeTool.execute(context(researcher),
                        new FileWriteTool.Args("files/../../escape.txt", "nope", false)));
        assertThat(onWrite).isNotNull();
        assertThat(onWrite.code()).isEqualTo(ErrorCode.SANDBOX_VIOLATION);
    }

    /** v0.0.27 🍊 Platform-only folders stay read-only for the file tools. */
    @Test
    void refusesWritesIntoPlatformOnlyFolders() {
        assertThatThrownBy(() -> writeTool.execute(context(researcher),
                new FileWriteTool.Args("tool-outputs/forged.txt", "nope", false)))
                .isInstanceOf(PermissionDeniedException.class);
    }

    /** v0.0.27 🍊 Every file tool re-checks its permission even when invoked outside the dispatcher. */
    @Test
    void directInvocationStillRequiresFilePermissions() {
        assertThatThrownBy(() -> writeTool.execute(context(liaison),
                new FileWriteTool.Args("files/forbidden.txt", "nope", false)))
                .isInstanceOf(PermissionDeniedException.class);

        AgentProfile noReader = agents.createNamed(roomId,
                new CreateAgentRequest(Role.RESEARCHER, null, null, null,
                        java.util.List.of(ai.yuzu.agent.Permission.CHAT_POST), null), "Kumquat");
        assertThatThrownBy(() -> readTool.execute(context(noReader),
                new FileReadTool.Args("files/notes.md", null, null, null)))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> listTool.execute(context(noReader), new FileListTool.Args(null)))
                .isInstanceOf(PermissionDeniedException.class);
    }

    /** v0.0.27 🍊 A direct tool context for one acting coworker. */
    private ToolContext context(AgentProfile profile) {
        AgentContext ctx = runtimes.require(profile.agentId()).context("trace-file-tools", null, time);
        return new ToolContext(ctx, "batch-file-tools",
                "call-" + ThreadLocalRandom.current().nextInt(1_000_000), 0, "test file tool", 0,
                deps.reporter().start(profile.agentId(), "TOOL", "test", ctx.traceId(), null));
    }
}
