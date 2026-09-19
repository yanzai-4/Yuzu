package ai.yuzu.workspace;

import ai.yuzu.common.error.ErrorCode;
import ai.yuzu.common.error.SandboxViolationException;
import ai.yuzu.common.id.AgentId;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** v0.0.11 🍊 The sandbox boundary: escapes, absolute paths, control characters, links and special files are refused. */
class WorkspacePathGuardTest {

    @TempDir
    Path base;

    private AgentWorkspace workspace;
    private Path outside;

    /** v0.0.11 🍊 Opens a workspace and plants a secret file next to (outside) the agent root. */
    @BeforeEach
    void setUp() throws IOException {
        workspace = WorkspaceFixture.service(base, id -> 64L << 20).forAgent(AgentId.of("agent-a11c"));
        outside = Files.writeString(base.resolve("outside-secret.txt"), "top secret");
    }

    /** v0.0.11 🍊 '..' can never climb above the agent root, for reads or writes. */
    @ParameterizedTest
    @ValueSource(strings = {"..", "../outside-secret.txt", "files/../../outside-secret.txt", "../agent-beef/files/x.txt",
            "files/../../../etc/passwd", "./../x"})
    void parentEscapesAreRejected(String path) {
        assertViolation(() -> workspace.readText(path, 100), "parent-escape");
        assertViolation(() -> workspace.writeText(path, "x"), "parent-escape");
        assertViolation(() -> workspace.list(path), "parent-escape");
    }

    /** v0.0.11 🍊 Absolute, home-directory and drive paths are refused. */
    @ParameterizedTest
    @ValueSource(strings = {"/etc/passwd", "/", "~/.ssh/id_rsa", "~", "C:/Windows/win.ini", "c:secret"})
    void absolutePathsAreRejected(String path) {
        assertViolation(() -> workspace.readText(path, 100), "absolute-path");
        assertViolation(() -> workspace.writeText(path, "x"), "absolute-path");
    }

    /** v0.0.11 🍊 NUL, CR/LF, escapes, bidi overrides and zero-width characters are refused. */
    @Test
    void controlCharactersAreRejected() {
        for (String path : List.of("files/a\u0000b.txt", "files/a\nb.txt", "files/a\rb.txt", "files/\u001b[31mred.txt",
                "files/\u202Etxt.exe", "files/zero\u200Bwidth.txt", "files/tab\t.txt", "files/\uD800lone.txt")) {
            assertViolation(() -> workspace.writeText(path, "x"), "control-character");
            assertViolation(() -> workspace.readText(path, 100), "control-character");
        }
    }

    /** v0.0.11 🍊 Backslashes are refused instead of being guessed as separators. */
    @Test
    void backslashesAreRejected() {
        assertViolation(() -> workspace.readText("files\\..\\..\\outside-secret.txt", 100), "backslash");
    }

    /** v0.0.11 🍊 A link to a file outside can be neither read nor written through. */
    @Test
    void symlinkToAFileOutsideIsRejected() throws IOException {
        Files.createSymbolicLink(workspace.root().resolve("files/leak.txt"), outside);
        assertViolation(() -> workspace.readText("files/leak.txt", 100), "symbolic-link");
        assertViolation(() -> workspace.readLines("files/leak.txt", 1, 1), "symbolic-link");
        assertViolation(() -> workspace.countLines("files/leak.txt"), "symbolic-link");
        assertViolation(() -> workspace.writeText("files/leak.txt", "overwritten"), "symbolic-link");
        assertViolation(() -> workspace.append("files/leak.txt", "more"), "symbolic-link");
        assertViolation(() -> workspace.stat("files/leak.txt"), "symbolic-link");
        assertThat(Files.readString(outside)).isEqualTo("top secret");
    }

    /** v0.0.11 🍊 A linked folder is refused wherever it appears on the path. */
    @Test
    void symlinkedFolderIsRejectedAnywhereOnThePath() throws IOException {
        Path outsideDir = Files.createDirectories(base.resolve("outside-dir"));
        Files.writeString(outsideDir.resolve("x.txt"), "outside");
        Files.createSymbolicLink(workspace.root().resolve("files/escape"), outsideDir);
        assertViolation(() -> workspace.readText("files/escape/x.txt", 100), "symbolic-link");
        assertViolation(() -> workspace.writeText("files/escape/new.txt", "x"), "symbolic-link");
        assertViolation(() -> workspace.writeText("files/escape/deeper/new.txt", "x"), "symbolic-link");
        assertViolation(() -> workspace.list("files/escape"), "symbolic-link");
        assertThat(outsideDir.resolve("new.txt")).doesNotExist();
        assertThat(outsideDir.resolve("deeper")).doesNotExist();
    }

    /** v0.0.11 🍊 Even a link that points inside the workspace is refused; listings still show it. */
    @Test
    void symlinkPointingInsideIsAlsoRejected() throws IOException {
        workspace.writeText("files/real.txt", "hello");
        Files.createSymbolicLink(workspace.root().resolve("files/alias.txt"), workspace.root().resolve("files/real.txt"));
        assertViolation(() -> workspace.readText("files/alias.txt", 100), "symbolic-link");
        assertThat(workspace.list("files").entries())
                .anySatisfy(entry -> {
                    assertThat(entry.name()).isEqualTo("alias.txt");
                    assertThat(entry.kind()).isEqualTo(WorkspaceEntry.Kind.SYMLINK);
                });
    }

    /** v0.0.11 🍊 A hard link can alias a file outside: never read or appended; a rewrite replaces only the name. */
    @Test
    void hardLinksAreNeverOpened() throws IOException {
        Files.createLink(workspace.root().resolve("files/hard.txt"), outside);
        assertViolation(() -> workspace.readText("files/hard.txt", 100), "hard-link");
        assertViolation(() -> workspace.append("files/hard.txt", "more"), "hard-link");
        workspace.writeText("files/hard.txt", "replaced");
        assertThat(Files.readString(outside)).isEqualTo("top secret");
        assertThat(workspace.readText("files/hard.txt", 100).text()).isEqualTo("replaced");
    }

    /** v0.0.11 🍊 A FIFO would block a reader forever, so special files are refused immediately. */
    @Test
    @Timeout(10)
    void specialFilesAreRefusedWithoutBlocking() throws Exception {
        Path fifo = workspace.root().resolve("files/pipe");
        Process mkfifo;
        try {
            mkfifo = new ProcessBuilder("mkfifo", fifo.toString()).start();
        } catch (IOException e) {
            assumeTrue(false, "mkfifo is not available");
            return;
        }
        assumeTrue(mkfifo.waitFor() == 0 && Files.exists(fifo), "mkfifo failed");
        assertViolation(() -> workspace.readText("files/pipe", 100), "special-file");
        assertViolation(() -> workspace.append("files/pipe", "x"), "special-file");
    }

    /** v0.0.11 🍊 Harmless variants are normalized and stay inside. */
    @Test
    void normalizedVariantsStayInside() {
        workspace.writeText("./files//notes/../report.md", "ok");
        assertThat(workspace.readText("files/report.md", 100).text()).isEqualTo("ok");
        assertThat(workspace.normalize(" files/./report.md ")).isEqualTo("files/report.md");
        assertThat(workspace.normalize("files/sub/..")).isEqualTo("files");
        assertThat(workspace.normalize("")).isEmpty();
        assertThat(workspace.resolve("files/report.md")).isEqualTo(workspace.root().resolve("files/report.md"));
    }

    /** v0.0.11 🍊 Violations name the agent, the printable requested path and a reason, never server paths. */
    @Test
    void violationDetailsNameTheAgentAndThePath() {
        SandboxViolationException e = catchThrowableOfType(() -> workspace.readText("../../etc/passwd", 10),
                SandboxViolationException.class);
        assertThat(e.code()).isEqualTo(ErrorCode.SANDBOX_VIOLATION);
        assertThat(e.agentId()).isEqualTo("agent-a11c");
        assertThat(e.details()).containsEntry("agentId", "agent-a11c").containsEntry("path", "../../etc/passwd")
                .containsEntry("reason", "parent-escape");
        assertThat(e.getMessage()).doesNotContain(base.toString());

        SandboxViolationException nul = catchThrowableOfType(() -> workspace.readText("a\u0000b", 10),
                SandboxViolationException.class);
        assertThat(nul.details()).containsEntry("path", "a\\u0000b");
    }

    /** v0.0.11 🍊 Asserts a SANDBOX_VIOLATION with the given reason code. */
    private static void assertViolation(ThrowingCallable call, String reason) {
        assertThatThrownBy(call).isInstanceOf(SandboxViolationException.class)
                .satisfies(e -> assertThat(((SandboxViolationException) e).details()).containsEntry("reason", reason));
    }
}
