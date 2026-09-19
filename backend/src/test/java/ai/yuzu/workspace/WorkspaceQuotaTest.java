package ai.yuzu.workspace;

import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.id.AgentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** v0.0.11 🍊 Quota enforcement: metered writes stop at the limit, llm/ is exempt, deletes and shrinking free space. */
class WorkspaceQuotaTest {

    @TempDir
    Path base;

    private final AtomicLong quota = new AtomicLong(1_000_000);
    private AgentWorkspace workspace;

    /** v0.0.11 🍊 Opens a workspace whose quota the test can change at any time. */
    @BeforeEach
    void setUp() {
        workspace = WorkspaceFixture.service(base, id -> quota.get()).forAgent(AgentId.of("agent-a11c"));
    }

    /** v0.0.11 🍊 Writes, appends and tool outputs are refused past the quota; replacing counts only the growth. */
    @Test
    void meteredWritesStopAtTheQuota() {
        workspace.writeText("files/a.txt", "a".repeat(600_000));
        assertThat(workspace.usedBytes()).isEqualTo(600_000);
        assertThat(workspace.quotaBytes()).isEqualTo(1_000_000);

        PermissionDeniedException denied = catchThrowableOfType(
                () -> workspace.writeText("files/b.txt", "b".repeat(600_000)), PermissionDeniedException.class);
        assertThat(denied).hasMessageContaining("quota");
        assertThat(denied.agentId()).isEqualTo("agent-a11c");
        assertThat(denied.details()).containsEntry("reason", "quota").containsEntry("usedBytes", 600_000L)
                .containsEntry("quotaBytes", 1_000_000L).containsEntry("requestedBytes", 600_000L)
                .containsEntry("path", "files/b.txt");
        assertThat(workspace.exists("files/b.txt")).isFalse();

        workspace.writeText("files/a.txt", "a".repeat(900_000));
        assertThat(workspace.usedBytes()).isEqualTo(900_000);
        assertThatThrownBy(() -> workspace.append("files/a.txt", "x".repeat(200_000)))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("quota");
        assertThatThrownBy(() -> workspace.saveToolOutput("fetch", "y".repeat(200_000)))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("quota");
        assertThat(workspace.stat("files/a.txt").sizeBytes()).isEqualTo(900_000);
    }

    /** v0.0.11 🍊 LLM payloads are telemetry: never metered and never refused. */
    @Test
    void llmPayloadsAreNotMetered() {
        workspace.writeText("files/a.txt", "a".repeat(900_000));
        workspace.saveLlmPayload("llmcall-a11c-0000000001", "z".repeat(500_000));
        assertThat(workspace.usedBytes()).isEqualTo(900_000);
        assertThat(workspace.readText("llm/2026-09-19/llmcall-a11c-0000000001.json", 16).fileSizeBytes())
                .isEqualTo(500_000);
    }

    /** v0.0.11 🍊 Deleting frees space immediately. */
    @Test
    void deletingFreesSpace() {
        workspace.writeText("files/a.txt", "a".repeat(900_000));
        assertThat(workspace.delete("files/a.txt")).isTrue();
        assertThat(workspace.usedBytes()).isZero();
        workspace.writeText("files/b.txt", "b".repeat(900_000));
        assertThat(workspace.usedBytes()).isEqualTo(900_000);
    }

    /** v0.0.11 🍊 A changed quota applies to the very next write. */
    @Test
    void quotaChangesApplyImmediately() {
        quota.set(100);
        assertThatThrownBy(() -> workspace.writeText("files/a.txt", "a".repeat(200)))
                .isInstanceOf(PermissionDeniedException.class);
        quota.set(10_000);
        assertThat(workspace.writeText("files/a.txt", "a".repeat(200)).sizeBytes()).isEqualTo(200);
    }

    /** v0.0.11 🍊 Over the quota (for example after the limit was lowered) shrinking still works, growing does not. */
    @Test
    void shrinkingIsAllowedEvenOverTheQuota() {
        workspace.writeText("files/a.txt", "a".repeat(500));
        quota.set(100);
        assertThat(workspace.writeText("files/a.txt", "a".repeat(50)).sizeBytes()).isEqualTo(50);
        quota.set(60);
        assertThatThrownBy(() -> workspace.writeText("files/b.txt", "b".repeat(20)))
                .isInstanceOf(PermissionDeniedException.class);
    }

    /** v0.0.11 🍊 Usage is cached; files written outside the API count after invalidateUsage(). */
    @Test
    void externalWritesCountAfterInvalidation() throws IOException {
        workspace.writeText("files/a.txt", "a".repeat(1_000));
        assertThat(workspace.usedBytes()).isEqualTo(1_000);
        Files.writeString(workspace.root().resolve("code/output.bin"), "b".repeat(5_000));
        assertThat(workspace.usedBytes()).isEqualTo(1_000);
        workspace.invalidateUsage();
        assertThat(workspace.usedBytes()).isEqualTo(6_000);
    }
}
