package ai.yuzu.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** v0.0.11 🍊 Crash-safe writes: temp file + fsync + atomic rename, and appends that never follow a symbolic link. */
final class AtomicFiles {

    private static final Logger log = LoggerFactory.getLogger(AtomicFiles.class);

    /** v0.0.11 🍊 Static helpers only. */
    private AtomicFiles() {
    }

    /** v0.0.11 🍊 Replaces the target so readers see either the old or the new content, never a partial file. */
    static void write(Path target, byte[] bytes) throws IOException {
        Path temp = target.resolveSibling(FileNames.tempName());
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) {
                writeFully(channel, bytes);
                channel.force(true);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            deleteLeftover(temp);
        }
    }

    /** v0.0.11 🍊 Appends bytes to a regular file (created when missing) without following a symbolic link. */
    static void append(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS)) {
            writeFully(channel, bytes);
        }
    }

    /** v0.0.11 🍊 Writes the whole array (a channel may accept fewer bytes per call). */
    private static void writeFully(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    /** v0.0.11 🍊 Removes the temp file after a failed write; a cleanup failure is logged, the original error wins. */
    private static void deleteLeftover(Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            log.warn("Could not remove temporary workspace file {}: {}", temp.getFileName(), e.getMessage());
        }
    }
}
