package ai.yuzu.workspace;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** v0.0.11 🍊 Streaming readers for large files: byte-level line scanning and UTF-8-safe chunks (never loads a file). */
final class LargeFileReader {

    private static final int BUFFER_BYTES = 64 * 1024;

    /** v0.0.11 🍊 Limits of one line read: most lines, most bytes of text, longest line kept in full. */
    record Budget(int maxLines, int maxBytes, int maxLineBytes) {
    }

    /** v0.0.11 🍊 Static helpers only. */
    private LargeFileReader() {
    }

    /** v0.0.11 🍊 Counts lines with a fixed 64 KB buffer; a last line without a trailing newline counts too. */
    static long countLines(Path file) throws IOException {
        try (FileChannel channel = open(file)) {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_BYTES);
            byte[] data = buffer.array();
            long newlines = 0;
            long total = 0;
            byte last = '\n';
            int read;
            while ((read = channel.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    if (data[i] == '\n') {
                        newlines++;
                    }
                }
                if (read > 0) {
                    last = data[read - 1];
                    total += read;
                }
                buffer.clear();
            }
            return total > 0 && last != '\n' ? newlines + 1 : newlines;
        }
    }

    /** v0.0.11 🍊 Reads lines fromLine..toLine by scanning bytes; skipped lines are never decoded or kept. */
    static LineSlice readLines(String path, Path file, long fromLine, long toLine, Budget budget) throws IOException {
        LineCollector collector = new LineCollector(fromLine, toLine, budget);
        try (FileChannel channel = open(file)) {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_BYTES);
            int read;
            while (!collector.finished() && (read = channel.read(buffer)) != -1) {
                collector.accept(buffer.array(), read);
                buffer.clear();
            }
        }
        return collector.toSlice(path);
    }

    /** v0.0.11 🍊 Decodes up to length bytes from offset, moving both ends to UTF-8 character boundaries. */
    static TextChunk readChunk(String path, Path file, long offset, int length) throws IOException {
        try (FileChannel channel = open(file)) {
            long size = channel.size();
            if (offset >= size) {
                return new TextChunk(path, "", offset, offset, size, true, false);
            }
            int wanted = (int) Math.min((long) length + 3, size - offset);
            byte[] bytes = readFully(channel, offset, wanted);
            int start = 0;
            while (start < 3 && start < bytes.length && isContinuation(bytes[start])) {
                start++;
            }
            int limit = Math.min(bytes.length, length);
            int end = offset + limit >= size ? limit : completePrefix(bytes, start, limit);
            if (end <= start) {
                end = Math.max(limit, start);
            }
            String text = new String(bytes, start, end - start, StandardCharsets.UTF_8);
            long next = offset + end;
            return new TextChunk(path, text, offset + start, next, size, next >= size, containsNul(bytes, start, end));
        }
    }

    /** v0.0.11 🍊 Opens a file for reading without following a symbolic link on the last component. */
    private static FileChannel open(Path file) throws IOException {
        return FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }

    /** v0.0.11 🍊 Reads up to count bytes at a position (fewer only at end of file). */
    private static byte[] readFully(FileChannel channel, long position, int count) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(count);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, position + buffer.position()) < 0) {
                break;
            }
        }
        return buffer.position() == count ? buffer.array() : Arrays.copyOf(buffer.array(), buffer.position());
    }

    /** v0.0.11 🍊 True for UTF-8 continuation bytes (10xxxxxx). */
    static boolean isContinuation(byte value) {
        return (value & 0xC0) == 0x80;
    }

    /** v0.0.11 🍊 Largest end ≤ limit such that bytes[start, end) does not stop inside a multi-byte character. */
    static int completePrefix(byte[] bytes, int start, int limit) {
        int lead = limit - 1;
        int back = 0;
        while (lead >= start && back < 3 && isContinuation(bytes[lead])) {
            lead--;
            back++;
        }
        if (lead < start) {
            return limit;
        }
        int needed = sequenceLength(bytes[lead]);
        if (needed <= 1) {
            return limit;
        }
        return lead + needed > limit ? lead : limit;
    }

    /** v0.0.11 🍊 Length of the UTF-8 sequence a lead byte starts (0 for continuation or invalid bytes). */
    private static int sequenceLength(byte lead) {
        int value = lead & 0xFF;
        if (value < 0x80) {
            return 1;
        }
        if (value < 0xC0) {
            return 0;
        }
        if (value < 0xE0) {
            return 2;
        }
        if (value < 0xF0) {
            return 3;
        }
        return value < 0xF8 ? 4 : 0;
    }

    /** v0.0.11 🍊 True when the range contains a NUL byte (a strong hint of binary content). */
    private static boolean containsNul(byte[] bytes, int start, int end) {
        for (int i = start; i < end; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    /** v0.0.11 🍊 Index of the next '\n' in data[from, to), or -1. */
    private static int indexOfNewline(byte[] data, int from, int to) {
        for (int i = from; i < to; i++) {
            if (data[i] == '\n') {
                return i;
            }
        }
        return -1;
    }

    /** v0.0.11 🍊 Collects the requested lines from successive buffers while tracking budgets and continuation. */
    private static final class LineCollector {

        private final long fromLine;
        private final long toLine;
        private final Budget budget;
        private final List<String> lines = new ArrayList<>();
        private byte[] line = new byte[256];
        private int kept;
        private long seen;
        private long lineNumber = 1;
        private long sliceBytes;
        private boolean stopped;
        private boolean hasMore;
        private boolean cut;

        /** v0.0.11 🍊 Creates a collector for a 1-based inclusive line range. */
        private LineCollector(long fromLine, long toLine, Budget budget) {
            this.fromLine = fromLine;
            this.toLine = toLine;
            this.budget = budget;
        }

        /** v0.0.11 🍊 True once the range or a budget is done and we know whether more content follows. */
        boolean finished() {
            return stopped && hasMore;
        }

        /** v0.0.11 🍊 Consumes one buffer of bytes. */
        void accept(byte[] data, int count) {
            int i = 0;
            while (i < count) {
                if (stopped) {
                    hasMore = true;
                    return;
                }
                int newline = indexOfNewline(data, i, count);
                if (lineNumber < fromLine) {
                    if (newline < 0) {
                        return;
                    }
                    lineNumber++;
                    i = newline + 1;
                    continue;
                }
                int end = newline < 0 ? count : newline;
                keep(data, i, end - i);
                if (newline < 0) {
                    return;
                }
                complete();
                i = newline + 1;
            }
        }

        /** v0.0.11 🍊 Keeps line bytes up to the per-line budget and counts the rest as cut. */
        private void keep(byte[] data, int offset, int length) {
            seen += length;
            int take = Math.min(budget.maxLineBytes() - kept, length);
            if (take > 0) {
                if (kept + take > line.length) {
                    line = Arrays.copyOf(line, Math.max(kept + take, line.length * 2));
                }
                System.arraycopy(data, offset, line, kept, take);
                kept += take;
            }
        }

        /** v0.0.11 🍊 Finishes the current line and stops when the range end or a budget is reached. */
        private void complete() {
            lines.add(decodeLine());
            sliceBytes += kept;
            kept = 0;
            seen = 0;
            lineNumber++;
            if (lineNumber > toLine || lines.size() >= budget.maxLines() || sliceBytes >= budget.maxBytes()) {
                stopped = true;
            }
        }

        /** v0.0.11 🍊 Decodes the kept bytes (dropping a CR of CRLF) and marks lines that were cut. */
        private String decodeLine() {
            boolean lineCut = seen > kept;
            int length = kept;
            if (lineCut) {
                length = completePrefix(line, 0, length);
                cut = true;
            } else if (length > 0 && line[length - 1] == '\r') {
                length--;
            }
            String text = new String(line, 0, length, StandardCharsets.UTF_8);
            return lineCut ? text + " … [line cut: " + FileNames.humanBytes(seen) + " in total]" : text;
        }

        /** v0.0.11 🍊 Adds a final line without a trailing newline and builds the slice. */
        LineSlice toSlice(String path) {
            if (!stopped && lineNumber >= fromLine && seen > 0) {
                lines.add(decodeLine());
            }
            long last = fromLine + lines.size() - 1;
            boolean truncated = cut || (stopped && hasMore && last < toLine);
            return new LineSlice(path, fromLine, last, lines, hasMore, truncated);
        }
    }
}
