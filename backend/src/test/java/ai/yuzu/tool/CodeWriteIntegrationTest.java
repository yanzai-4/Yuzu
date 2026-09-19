package ai.yuzu.tool;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Permission;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.tool.impl.code.CodeWriteTool;
import ai.yuzu.tool.impl.code.SandboxRunner;
import ai.yuzu.tool.impl.code.ScriptRuntime;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.workspace.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** v0.0.26 🍊 code_write puts files in the workspace and runs them offline in sandbox-exec (or says it could not). */
@IntegrationTest
class CodeWriteIntegrationTest {

    @Autowired
    private AgentService agents;
    @Autowired
    private CodeWriteTool tool;
    @Autowired
    private SandboxRunner sandbox;
    @Autowired
    private WorkspaceService workspaces;
    @Autowired
    private AgentRuntimeManager runtimes;
    @Autowired
    private ModuleDeps deps;
    @Autowired
    private NaturalTime time;
    @Autowired
    private JdbcClient jdbc;

    private AgentProfile kumquat;
    private AgentProfile lime;

    @BeforeEach
    void setUp() {
        String roomId = newRoom();
        kumquat = agents.createNamed(roomId, new CreateAgentRequest(Role.ENGINEER, null, null, null, null, null),
                "Kumquat");
        lime = agents.createNamed(roomId, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null),
                "Lime");
    }

    /** v0.0.26 🍊 Generated code is trusted by construction: it skips the AI outbound review, not the code checks. */
    @Test
    void specIsTrustedAndHighRisk() {
        assertThat(tool.spec().name()).isEqualTo("code_write");
        assertThat(tool.spec().trusted()).isTrue();
        assertThat(tool.spec().risk()).isEqualTo(Risk.HIGH);
        assertThat(tool.spec().permissions()).containsExactly(Permission.CODE_WRITE);
    }

    /** v0.0.26 🍊 Writing without running stores the file under code/ and never starts a process. */
    @Test
    void writesTheFileIntoTheCodeArea() {
        ToolResult result = tool.execute(context(kumquat),
                new CodeWriteTool.Args("tools/report.py", "print('hello from Kumquat')\n", false));
        assertThat(result.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(result.output()).contains("code/tools/report.py").contains("did not run");
        assertThat(workspaces.forAgent(kumquat.agentId()).readText("code/tools/report.py", 4096).text())
                .contains("hello from Kumquat");
    }

    /** v0.0.26 🍊 With sandbox-exec present the script runs and its output comes back; otherwise the tool says so. */
    @Test
    void runsTheScriptInTheSandboxWhenAvailable() {
        ToolResult result = tool.execute(context(kumquat),
                new CodeWriteTool.Args("hello.py", "print('sandbox marker 42')\n", true));
        assertThat(result.status()).isEqualTo(ToolResult.Status.OK);
        if (sandbox.available() && ScriptRuntime.PYTHON.interpreter().isPresent()) {
            assertThat(result.output()).contains("sandbox marker 42").contains("exit code 0");
        } else {
            assertThat(result.output()).contains("did not run").contains("sandbox-exec");
        }
    }

    /** v0.0.26 🍊 The sandbox has no network: a script that tries to reach the internet fails inside it. */
    @Test
    void deniesNetworkAccessInsideTheSandbox() {
        assumeTrue(sandbox.available() && ScriptRuntime.PYTHON.interpreter().isPresent(),
                "sandbox-exec and python3 are required for this check");
        String script = """
                import urllib.request
                try:
                    urllib.request.urlopen("http://93.184.216.34/", timeout=5).read()
                    print("NETWORK-REACHED")
                except Exception as error:
                    print("NETWORK-BLOCKED", type(error).__name__)
                """;
        ToolResult result = tool.execute(context(kumquat), new CodeWriteTool.Args("net.py", script, true));
        assertThat(result.output()).contains("NETWORK-BLOCKED").doesNotContain("NETWORK-REACHED");
    }

    /** v0.0.26 🍊 Files the sandbox cannot interpret are written but never executed. */
    @Test
    void refusesToRunUnknownFileTypes() {
        ToolResult result = tool.execute(context(kumquat),
                new CodeWriteTool.Args("notes.txt", "just notes\n", true));
        assertThat(result.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(result.output()).contains("did not run");
    }

    /** v0.0.26 🍊 Paths that try to leave the workspace are refused. */
    @Test
    void refusesEscapingPaths() {
        ToolResult result = tool.execute(context(kumquat),
                new CodeWriteTool.Args("../../etc/evil.py", "print(1)", false));
        assertThat(result.status()).isEqualTo(ToolResult.Status.ERROR);
    }

    /** v0.0.26 🍊 An agent without CODE_WRITE is refused inside execute (defense in depth). */
    @Test
    void refusesAgentsWithoutThePermission() {
        assertThatThrownBy(() -> tool.execute(context(lime), new CodeWriteTool.Args("x.py", "print(1)", false)))
                .isInstanceOf(PermissionDeniedException.class);
    }

    /** v0.0.26 🍊 Creates one isolated room for this test. */
    private String newRoom() {
        String id = String.format("room-%04x", ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        jdbc.sql("INSERT INTO room (id, name, created_at) VALUES (:id, 'Code room', UTC_TIMESTAMP(3))")
                .param("id", id).update();
        return id;
    }

    /** v0.0.26 🍊 A direct tool context for one coworker. */
    private ToolContext context(AgentProfile profile) {
        AgentContext ctx = runtimes.require(profile.agentId()).context("trace-code", null, time);
        return new ToolContext(ctx, "batch-code", "call-" + ThreadLocalRandom.current().nextInt(1_000_000), 0,
                "write a small script", 0,
                deps.reporter().start(profile.agentId(), "TOOL", "test", ctx.traceId(), null));
    }
}
