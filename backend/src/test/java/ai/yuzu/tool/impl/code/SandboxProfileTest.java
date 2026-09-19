package ai.yuzu.tool.impl.code;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.26 🍊 The sandbox profile denies everything but reads and workspace writes; paths stay inside code/. */
class SandboxProfileTest {

    /** v0.0.26 🍊 Deny by default, no network, and only the workspace is writable. */
    @Test
    void deniesEverythingExceptWorkspaceWrites() {
        String profile = SandboxProfile.denyNetwork(Path.of("/tmp/yuzu/agent-1234"));
        assertThat(profile).contains("(deny default)").contains("(deny network*)")
                .contains("(allow file-write* (subpath \"/tmp/yuzu/agent-1234\"))");
        assertThat(profile).doesNotContain("(allow network");
    }

    /** v0.0.26 🍊 Every written file lands under code/, whether or not the model repeats the folder. */
    @Test
    void keepsFilesInsideTheCodeArea() {
        assertThat(CodeWriteTool.inCodeArea("report.py")).isEqualTo("code/report.py");
        assertThat(CodeWriteTool.inCodeArea("code/report.py")).isEqualTo("code/report.py");
        assertThat(CodeWriteTool.inCodeArea("/tools/report.py")).isEqualTo("code/tools/report.py");
        assertThat(CodeWriteTool.inCodeArea("tools\\report.py")).isEqualTo("code/tools/report.py");
    }

    /** v0.0.26 🍊 Only the three known extensions can ever be executed. */
    @Test
    void runsOnlyKnownExtensions() {
        assertThat(ScriptRuntime.forFile("code/a.py")).contains(ScriptRuntime.PYTHON);
        assertThat(ScriptRuntime.forFile("code/a.sh")).contains(ScriptRuntime.BASH);
        assertThat(ScriptRuntime.forFile("code/a.js")).contains(ScriptRuntime.NODE);
        assertThat(ScriptRuntime.forFile("code/a.rb")).isEqualTo(Optional.empty());
        assertThat(ScriptRuntime.forFile("code/notes.txt")).isEqualTo(Optional.empty());
    }
}
