package ai.yuzu.workspace;

import ai.yuzu.common.id.AgentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.11 🍊 Large-data stability: 50 MB+ files are read by line range or chunk quickly, with little memory. */
class LargeFileReadTest {

    private static final int LINES = 1_000_000;
    private static final String TAIL = " lorem ipsum dolor sit amet, consectetur adipiscing elit\n";
    private static final long MAX_ALLOCATION = 8L << 20;

    @TempDir
    Path base;

    private AgentWorkspace workspace;

    /** v0.0.11 🍊 Opens a workspace with a quota large enough to never matter here. */
    @BeforeEach
    void setUp() {
        workspace = WorkspaceFixture.service(base, id -> 1L << 30).forAgent(AgentId.of("agent-b16f"));
    }

    /** v0.0.11 🍊 A 64 MB, one-million-line file: tail, middle, count and chunk reads stay fast and allocation-light. */
    @Test
    @Timeout(120)
    void fiftyMegabyteFileIsReadByLineRangeWithoutLoadingIt() throws IOException {
        Path file = workspace.root().resolve("files/big.log");
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (int i = 1; i <= LINES; i++) {
                out.write(number(i));
                out.write(TAIL);
            }
        }
        assertThat(Files.size(file)).isGreaterThanOrEqualTo(50L << 20);

        long allocatedBefore = allocatedBytes();
        long started = System.nanoTime();
        LineSlice tail = workspace.readLines("files/big.log", LINES - 4, LINES + 10);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        long allocated = allocatedBytes() - allocatedBefore;
        assertThat(tail.lines()).hasSize(5);
        assertThat(tail.lines().get(0)).isEqualTo(number(LINES - 4) + TAIL.stripTrailing());
        assertThat(tail.toLine()).isEqualTo(LINES);
        assertThat(tail.hasMore()).isFalse();
        assertThat(tail.truncated()).isFalse();
        assertThat(elapsedMillis).isLessThan(10_000);
        assertAllocationBelow(allocatedBefore, allocated);

        LineSlice middle = workspace.readLines("files/big.log", 500_000, 500_002);
        assertThat(middle.lines()).extracting(line -> line.substring(0, 7))
                .containsExactly("0500000", "0500001", "0500002");
        assertThat(middle.hasMore()).isTrue();

        allocatedBefore = allocatedBytes();
        assertThat(workspace.countLines("files/big.log")).isEqualTo(LINES);
        assertAllocationBelow(allocatedBefore, allocatedBytes() - allocatedBefore);

        TextChunk chunk = workspace.readChunk("files/big.log", 32L << 20, 4_096);
        assertThat(chunk.text()).hasSizeBetween(4_000, 4_096).doesNotContain("�");
        assertThat(chunk.eof()).isFalse();
    }

    /** v0.0.11 🍊 A 20 MB file with no newline is returned as one cut line, never materialized. */
    @Test
    @Timeout(60)
    void aHugeSingleLineIsCutNotLoaded() throws IOException {
        Path file = workspace.root().resolve("files/one-line.json");
        byte[] block = new byte[1 << 20];
        Arrays.fill(block, (byte) 'x');
        try (OutputStream out = Files.newOutputStream(file)) {
            for (int i = 0; i < 20; i++) {
                out.write(block);
            }
        }
        long allocatedBefore = allocatedBytes();
        LineSlice first = workspace.readLines("files/one-line.json", 1, 1);
        long allocated = allocatedBytes() - allocatedBefore;
        assertThat(first.lines()).singleElement().asString()
                .startsWith("xxxx").endsWith("… [line cut: 20 MB in total]")
                .hasSizeLessThan(AgentWorkspace.MAX_LINE_BYTES + 100);
        assertThat(first.truncated()).isTrue();
        assertThat(first.hasMore()).isFalse();
        assertAllocationBelow(allocatedBefore, allocated);
        assertThat(workspace.countLines("files/one-line.json")).isEqualTo(1);
    }

    /** v0.0.11 🍊 Zero-padded line number ("0000042"). */
    private static String number(int i) {
        String digits = Integer.toString(i);
        return "0".repeat(7 - digits.length()) + digits;
    }

    /** v0.0.11 🍊 Checks the allocation measured on this thread when the JVM supports measuring it. */
    private static void assertAllocationBelow(long before, long allocated) {
        if (before >= 0) {
            assertThat(allocated).as("bytes allocated while reading").isLessThan(MAX_ALLOCATION);
        }
    }

    /** v0.0.11 🍊 Bytes allocated so far by the current thread, or -1 when the JVM cannot tell. */
    private static long allocatedBytes() {
        if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported() && bean.isThreadAllocatedMemoryEnabled()) {
            return bean.getCurrentThreadAllocatedBytes();
        }
        return -1;
    }
}
