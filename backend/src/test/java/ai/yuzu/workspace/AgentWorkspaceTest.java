package ai.yuzu.workspace;

import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.error.SandboxViolationException;
import ai.yuzu.common.error.ToolExecutionException;
import ai.yuzu.common.id.AgentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** v0.0.11 🍊 Workspace operations: lazy folders, reads, atomic writes, areas, spill files, UTF-8 chunks and lines. */
class AgentWorkspaceTest {

    @TempDir
    Path base;

    private WorkspaceService service;
    private AgentWorkspace workspace;

    /** v0.0.11 🍊 Opens a workspace with a roomy quota. */
    @BeforeEach
    void setUp() {
        service = WorkspaceFixture.service(base, id -> 64L << 20);
        workspace = service.forAgent(AgentId.of("agent-a11c"));
    }

    /** v0.0.11 🍊 The folder tree appears on first use, and the same workspace object is reused. */
    @Test
    void foldersAreCreatedLazily() {
        assertThat(base.resolve("agent-b0b0")).doesNotExist();
        AgentWorkspace other = service.forAgent(AgentId.of("agent-b0b0"));
        assertThat(other.root()).isEqualTo(base.resolve("agent-b0b0"));
        assertThat(other.list("").entries()).extracting(WorkspaceEntry::name)
                .containsExactly("code", "files", "llm", "memory", "tool-outputs", "web");
        assertThat(service.forAgent(AgentId.of("agent-b0b0"))).isSameAs(other);
    }

    /** v0.0.11 🍊 A configured workspace root that is a link never receives an agent tree through its target. */
    @Test
    void linkedConfiguredWorkspaceRootIsRejectedBeforeAnyFolderIsCreated() throws IOException {
        Path outside = Files.createDirectory(base.resolve("outside"));
        Path linkedBase = base.resolve("linked-workspaces");
        Files.createSymbolicLink(linkedBase, outside);
        WorkspaceService isolated = WorkspaceFixture.service(linkedBase, id -> 64L << 20);

        assertThatThrownBy(() -> isolated.forAgent(AgentId.of("agent-cafe")))
                .isInstanceOf(SandboxViolationException.class);
        assertThat(outside).isEmptyDirectory();
    }

    /** v0.0.11 🍊 A pre-planted agent-root link is refused before standard folders can escape into its target. */
    @Test
    void linkedAgentRootIsRejectedBeforeAnyFolderIsCreated() throws IOException {
        Path outside = Files.createDirectory(base.resolve("outside"));
        AgentId agent = AgentId.of("agent-cafe");
        Files.createSymbolicLink(base.resolve(agent.value()), outside);

        assertThatThrownBy(() -> service.forAgent(agent)).isInstanceOf(SandboxViolationException.class);
        assertThat(outside).isEmptyDirectory();
    }

    /** v0.0.11 🍊 A deleted tree is recreated on the next lookup. */
    @Test
    void aVanishedTreeIsRecreated() throws IOException {
        workspace.writeText("files/a.txt", "a");
        try (Stream<Path> walk = Files.walk(workspace.root())) {
            walk.sorted((x, y) -> y.getNameCount() - x.getNameCount()).forEach(p -> p.toFile().delete());
        }
        assertThat(workspace.root()).doesNotExist();
        AgentWorkspace again = service.forAgent(AgentId.of("agent-a11c"));
        assertThat(again).isSameAs(workspace);
        assertThat(again.exists("files")).isTrue();
        again.writeText("files/b.txt", "b");
        assertThat(again.readText("files/b.txt", 16).text()).isEqualTo("b");
    }

    /** v0.0.11 🍊 Writing, reading, stat, exists and listing agree with each other. */
    @Test
    void writeReadStatAndList() {
        String content = "# Todo\n- ship 🍊\n";
        WorkspaceEntry written = workspace.writeText("files/notes/todo.md", content);
        assertThat(written.path()).isEqualTo("files/notes/todo.md");
        assertThat(written.name()).isEqualTo("todo.md");
        assertThat(written.kind()).isEqualTo(WorkspaceEntry.Kind.FILE);
        assertThat(written.sizeBytes()).isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);

        TextChunk read = workspace.readText("files/notes/todo.md", 1024);
        assertThat(read.text()).isEqualTo(content);
        assertThat(read.eof()).isTrue();
        assertThat(read.truncated()).isFalse();
        assertThat(read.binary()).isFalse();

        assertThat(workspace.stat("files/notes").isDirectory()).isTrue();
        assertThat(workspace.exists("files/notes/todo.md")).isTrue();
        assertThat(workspace.exists("files/notes/missing.md")).isFalse();
        WorkspaceListing files = workspace.list("files");
        assertThat(files.entries()).extracting(WorkspaceEntry::path).containsExactly("files/notes");
        assertThat(files.truncated()).isFalse();
        assertThat(workspace.list("files/notes").entries()).extracting(WorkspaceEntry::name).containsExactly("todo.md");
        TextChunk head = workspace.readText("files/notes/todo.md", 4);
        assertThat(head.text()).isEqualTo("# Todo\n- ship ");
        assertThat(head.truncated()).isTrue();
        assertThat(head.nextOffsetBytes()).isEqualTo(14);
    }

    /** v0.0.11 🍊 Readers never see a half-written file while it is replaced over and over. */
    @Test
    void writesAreAtomic() throws Exception {
        String a = "a".repeat(512 * 1024);
        String b = "b".repeat(512 * 1024);
        workspace.writeText("files/atomic.txt", a);
        Path file = workspace.root().resolve("files/atomic.txt");
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger reads = new AtomicInteger();
        List<String> anomalies = new CopyOnWriteArrayList<>();
        Thread reader = Thread.ofPlatform().start(() -> {
            while (!stop.get()) {
                try {
                    String seen = Files.readString(file);
                    reads.incrementAndGet();
                    if (!seen.equals(a) && !seen.equals(b)) {
                        anomalies.add("partial content of " + seen.length() + " chars");
                    }
                } catch (NoSuchFileException e) {
                    anomalies.add("file missing during replace");
                } catch (IOException e) {
                    anomalies.add(e.toString());
                }
            }
        });
        for (int i = 0; i < 40; i++) {
            workspace.writeText("files/atomic.txt", i % 2 == 0 ? b : a);
        }
        stop.set(true);
        reader.join();
        assertThat(anomalies).isEmpty();
        assertThat(reads.get()).isPositive();
        assertThat(tempFiles(file.getParent())).isEmpty();
        assertThat(workspace.list("files").entries()).extracting(WorkspaceEntry::name).containsExactly("atomic.txt");
    }

    /** v0.0.11 🍊 A write that fails midway leaves the original file and no temp file behind. */
    @Test
    void failedWriteLeavesTheOriginalUntouched() throws IOException {
        workspace.writeText("files/locked/keep.txt", "original");
        Path dir = workspace.root().resolve("files/locked");
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(dir);
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assumeFalse(Files.isWritable(dir), "the test user ignores file permissions");
            assertThatThrownBy(() -> workspace.writeText("files/locked/keep.txt", "replacement"))
                    .isInstanceOf(ToolExecutionException.class)
                    .hasMessageNotContaining(base.toString());
        } finally {
            Files.setPosixFilePermissions(dir, original);
        }
        assertThat(Files.readString(dir.resolve("keep.txt"))).isEqualTo("original");
        assertThat(tempFiles(dir)).isEmpty();
    }

    /** v0.0.11 🍊 One write is limited to 1 MB; exactly 1 MB is fine. */
    @Test
    void oneWriteIsLimitedToOneMegabyte() {
        assertThatThrownBy(() -> workspace.writeText("files/big.txt", "x".repeat(AgentWorkspace.MAX_WRITE_BYTES + 1)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("1 MB");
        assertThat(workspace.writeText("files/exact.txt", "x".repeat(AgentWorkspace.MAX_WRITE_BYTES)).sizeBytes())
                .isEqualTo(AgentWorkspace.MAX_WRITE_BYTES);
        assertThatThrownBy(() -> workspace.append("files/exact.txt", "y".repeat(AgentWorkspace.MAX_WRITE_BYTES + 1)))
                .isInstanceOf(BadRequestException.class);
    }

    /** v0.0.11 🍊 Appends create the file and grow it beyond one write's limit. */
    @Test
    void appendCreatesAndGrowsFiles() {
        workspace.append("files/log.csv", "id,value\n");
        workspace.append("files/log.csv", "1,lemon\n");
        assertThat(workspace.readText("files/log.csv", 1024).text()).isEqualTo("id,value\n1,lemon\n");
        workspace.append("files/log.csv", "x".repeat(AgentWorkspace.MAX_WRITE_BYTES));
        workspace.append("files/log.csv", "y".repeat(AgentWorkspace.MAX_WRITE_BYTES));
        assertThat(workspace.stat("files/log.csv").sizeBytes()).isGreaterThan(2L * AgentWorkspace.MAX_WRITE_BYTES);
    }

    /** v0.0.11 🍊 Readers see a complete old append or complete new append, never a partly grown live file. */
    @Test
    void appendsAreAtomicForConcurrentReaders() throws Exception {
        String seed = "seed\n";
        String addition = "z".repeat(256 * 1024);
        workspace.writeText("files/append-atomic.txt", seed);
        Path file = workspace.root().resolve("files/append-atomic.txt");
        AtomicBoolean stop = new AtomicBoolean();
        List<Integer> observedLengths = new CopyOnWriteArrayList<>();
        Thread reader = Thread.ofPlatform().start(() -> {
            while (!stop.get()) {
                try {
                    observedLengths.add(Files.readString(file).length());
                } catch (IOException e) {
                    observedLengths.add(-1);
                }
            }
        });
        for (int i = 1; i <= 30; i++) {
            workspace.append("files/append-atomic.txt", addition);
        }
        stop.set(true);
        reader.join();

        assertThat(observedLengths).isNotEmpty();
        assertThat(observedLengths).allSatisfy(length -> {
            assertThat(length - seed.length()).isGreaterThanOrEqualTo(0);
            assertThat((length - seed.length()) % addition.length()).isZero();
        });
        assertThat(tempFiles(file.getParent())).isEmpty();
    }

    /** v0.0.11 🍊 Concurrent replacement of a writable parent never lets a write create files in the link target. */
    @Test
    void concurrentParentSwapsNeverWriteOutsideTheWorkspace() throws Exception {
        Path files = workspace.root().resolve("files");
        Path parked = workspace.root().resolve("files-parked");
        Path outside = Files.createDirectory(base.resolve("outside"));
        AtomicBoolean stop = new AtomicBoolean();
        Thread swapper = Thread.ofPlatform().start(() -> {
            while (!stop.get()) {
                try {
                    Files.move(files, parked);
                    Files.createSymbolicLink(files, outside);
                    Thread.yield();
                    Files.deleteIfExists(files);
                    Files.move(parked, files);
                } catch (IOException ignored) {
                    // The writer may observe either safe directory state; the test only rejects an outside write.
                }
            }
        });
        try {
            for (int i = 0; i < 1_000; i++) {
                try {
                    workspace.writeText("files/race-" + i + ".txt", "inside");
                } catch (RuntimeException ignored) {
                    // A changing parent may safely reject an operation; it must never redirect it.
                }
            }
        } finally {
            stop.set(true);
            swapper.join();
            if (Files.isSymbolicLink(files)) {
                Files.delete(files);
            }
            if (Files.exists(parked) && !Files.exists(files)) {
                Files.move(parked, files);
            } else if (Files.exists(parked)) {
                FileSystemUtils.deleteRecursively(parked);
            }
        }
        assertThat(outside).isEmptyDirectory();
    }

    /** v0.0.11 🍊 Generic writes only go to files/, code/, web/ and memory/; platform folders are read-only. */
    @Test
    void platformFoldersAreReadOnly() {
        assertDenied(() -> workspace.writeText("llm/2026-09-19/fake.json", "{}"), "read-only-area");
        assertDenied(() -> workspace.writeText("tool-outputs/forged.txt", "x"), "read-only-area");
        assertDenied(() -> workspace.append("tool-outputs/forged.txt", "x"), "read-only-area");
        assertDenied(() -> workspace.delete("llm/anything.json"), "read-only-area");
        assertDenied(() -> workspace.writeText("notes.txt", "x"), "outside-areas");
        assertDenied(() -> workspace.writeText("LLM/x.json", "x"), "outside-areas");
        assertThatThrownBy(() -> workspace.writeText("files", "x")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> workspace.writeText("", "x")).isInstanceOf(BadRequestException.class);
        for (String area : List.of("files", "code", "web", "memory")) {
            assertThat(workspace.writeText(area + "/ok.txt", area).path()).isEqualTo(area + "/ok.txt");
        }
    }

    /** v0.0.11 🍊 Files and empty folders can be deleted; non-empty folders cannot. */
    @Test
    void deleteRemovesFilesAndEmptyFolders() {
        workspace.writeText("files/tmp/a.txt", "a");
        assertThatThrownBy(() -> workspace.delete("files/tmp")).isInstanceOf(BadRequestException.class);
        assertThat(workspace.delete("files/tmp/a.txt")).isTrue();
        assertThat(workspace.delete("files/tmp/a.txt")).isFalse();
        assertThat(workspace.delete("files/tmp")).isTrue();
        assertThat(workspace.exists("files/tmp")).isFalse();
    }

    /** v0.0.11 🍊 Large tool results are saved under tool-outputs/<day>/ with a readable, unique name. */
    @Test
    void toolOutputsAreSavedByDay() {
        String path = workspace.saveToolOutput("Web Fetch: https://acme.test/pricing", "x".repeat(20_000));
        assertThat(path).matches("tool-outputs/2026-09-19/113205-web-fetch-https-acme\\.test-pricing-[0-9a-f]{6}\\.txt");
        assertThat(workspace.readText(path, 100_000).text()).hasSize(20_000);
        assertThat(workspace.saveToolOutput("web fetch: https://acme.test/pricing", "again")).isNotEqualTo(path);
        assertThat(workspace.saveToolOutput("   ", "x")).matches("tool-outputs/2026-09-19/113205-output-[0-9a-f]{6}\\.txt");

        assertThat(workspace.saveToolOutputIfLarge("small", "tiny")).isEmpty();
        assertThat(workspace.saveToolOutputIfLarge("small", null)).isEmpty();
        assertThat(workspace.saveToolOutputIfLarge("big", "y".repeat(AgentWorkspace.INLINE_RESULT_LIMIT_BYTES + 1)))
                .hasValueSatisfying(saved -> assertThat(saved).startsWith("tool-outputs/2026-09-19/113205-big-"));
    }

    /** v0.0.11 🍊 LLM payloads land in llm/<day>/<callId>.json; hostile ids are slugged into the folder. */
    @Test
    void llmPayloadsAreSavedByDay() {
        String path = workspace.saveLlmPayload("llmcall-a11c-0123456789", "{\"attempt\":1}");
        assertThat(path).isEqualTo("llm/2026-09-19/llmcall-a11c-0123456789.json");
        workspace.saveLlmPayload("llmcall-a11c-0123456789", "{\"attempt\":2}");
        assertThat(workspace.readText(path, 1024).text()).isEqualTo("{\"attempt\":2}");
        assertThat(workspace.saveLlmPayload("../../evil", "{}")).isEqualTo("llm/2026-09-19/evil.json");
        assertThat(workspace.saveLlmPayload(null, "{}")).isEqualTo("llm/2026-09-19/call.json");
    }

    /** v0.0.11 🍊 Chunked reads never split a multi-byte character, so chunks concatenate to the exact text. */
    @Test
    void readChunkKeepsUtf8CharactersWhole() {
        String text = "Grüße aus Zürich 🍊 — naïve café, 東京 ".repeat(50);
        workspace.writeText("files/utf8.txt", text);
        StringBuilder rebuilt = new StringBuilder();
        long offset = 0;
        while (true) {
            TextChunk chunk = workspace.readChunk("files/utf8.txt", offset, 17);
            assertThat(chunk.text()).doesNotContain("�");
            rebuilt.append(chunk.text());
            if (chunk.eof()) {
                break;
            }
            assertThat(chunk.nextOffsetBytes()).isGreaterThan(offset);
            offset = chunk.nextOffsetBytes();
        }
        assertThat(rebuilt.toString()).isEqualTo(text);

        TextChunk middle = workspace.readChunk("files/utf8.txt", 3, 16);
        assertThat(middle.offsetBytes()).isEqualTo(4);
        assertThat(middle.text()).startsWith("ße");
        TextChunk past = workspace.readChunk("files/utf8.txt", 1L << 40, 16);
        assertThat(past.text()).isEmpty();
        assertThat(past.eof()).isTrue();
        assertThatThrownBy(() -> workspace.readChunk("files/utf8.txt", -1, 16)).isInstanceOf(BadRequestException.class);
    }

    /** v0.0.11 🍊 Binary content is flagged so tools can say so instead of dumping bytes. */
    @Test
    void binaryContentIsFlagged() throws IOException {
        Files.write(workspace.root().resolve("files/blob.bin"), new byte[]{1, 0, 2, 0, 3});
        assertThat(workspace.readText("files/blob.bin", 64).binary()).isTrue();
    }

    /** v0.0.11 🍊 Line reads handle CRLF, empty lines, a missing final newline and ranges past the end. */
    @Test
    void readLinesHandlesEdges() {
        workspace.writeText("files/lines.txt", "alpha\r\nbeta\n\ngamma");
        assertThat(workspace.countLines("files/lines.txt")).isEqualTo(4);

        LineSlice all = workspace.readLines("files/lines.txt", 1, 100);
        assertThat(all.lines()).containsExactly("alpha", "beta", "", "gamma");
        assertThat(all.toLine()).isEqualTo(4);
        assertThat(all.hasMore()).isFalse();
        assertThat(all.truncated()).isFalse();

        LineSlice middle = workspace.readLines("files/lines.txt", 2, 3);
        assertThat(middle.lines()).containsExactly("beta", "");
        assertThat(middle.hasMore()).isTrue();
        assertThat(middle.numbered()).isEqualTo("2| beta\n3| \n");

        LineSlice past = workspace.readLines("files/lines.txt", 10, 20);
        assertThat(past.isEmpty()).isTrue();
        assertThat(past.toLine()).isEqualTo(9);
        assertThat(past.hasMore()).isFalse();

        workspace.writeText("files/empty.txt", "");
        assertThat(workspace.countLines("files/empty.txt")).isZero();
        workspace.writeText("files/newline.txt", "one\n");
        assertThat(workspace.countLines("files/newline.txt")).isEqualTo(1);
        assertThat(workspace.readLines("files/newline.txt", 1, 5).lines()).containsExactly("one");

        assertThatThrownBy(() -> workspace.readLines("files/lines.txt", 0, 1)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> workspace.readLines("files/lines.txt", 5, 4)).isInstanceOf(BadRequestException.class);
    }

    /** v0.0.11 🍊 One line read returns at most 5,000 lines and says it was truncated. */
    @Test
    void readLinesRespectsTheLineBudget() {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 6_000; i++) {
            text.append("line ").append(i).append('\n');
        }
        workspace.writeText("files/many.txt", text.toString());
        LineSlice slice = workspace.readLines("files/many.txt", 1, 6_000);
        assertThat(slice.lines()).hasSize(AgentWorkspace.MAX_LINES_PER_READ);
        assertThat(slice.toLine()).isEqualTo(AgentWorkspace.MAX_LINES_PER_READ);
        assertThat(slice.truncated()).isTrue();
        assertThat(slice.hasMore()).isTrue();
        assertThat(workspace.readLines("files/many.txt", 5_999, 6_000).lines()).containsExactly("line 5999", "line 6000");
    }

    /** v0.0.11 🍊 Missing paths are NOT_FOUND and folders/files are not confused. */
    @Test
    void missingPathsAndWrongKinds() throws IOException {
        assertThatThrownBy(() -> workspace.readText("files/nope.txt", 10)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> workspace.list("files/nope")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> workspace.stat("files/nope.txt")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> workspace.readText("files", 10)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> workspace.readText("", 10)).isInstanceOf(BadRequestException.class);
        workspace.writeText("files/plain.txt", "x");
        assertThatThrownBy(() -> workspace.list("files/plain.txt")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> workspace.writeText("files/plain.txt/child.txt", "x"))
                .isInstanceOf(BadRequestException.class);
        Files.createDirectories(workspace.root().resolve("files/folder"));
        assertThatThrownBy(() -> workspace.writeText("files/folder", "x")).isInstanceOf(BadRequestException.class);
    }

    /** v0.0.11 🍊 Asserts PERMISSION_DENIED with a reason code. */
    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String reason) {
        assertThatThrownBy(call).isInstanceOf(PermissionDeniedException.class)
                .satisfies(e -> assertThat(((PermissionDeniedException) e).details()).containsEntry("reason", reason));
    }

    /** v0.0.11 🍊 Leftover temporary files in a folder. */
    private static List<Path> tempFiles(Path dir) throws IOException {
        try (Stream<Path> children = Files.list(dir)) {
            return children.filter(p -> FileNames.isTemp(p.getFileName().toString())).toList();
        }
    }
}
