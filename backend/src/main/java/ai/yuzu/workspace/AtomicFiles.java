package ai.yuzu.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.NoSuchFileException;
import java.util.Set;

/** v0.0.11 🍊 Crash-safe writes: temp file + fsync + atomic rename, and appends that never follow a symbolic link. */
final class AtomicFiles {

    private static final Logger log = LoggerFactory.getLogger(AtomicFiles.class);

    /** v0.0.11 🍊 Static helpers only. */
    private AtomicFiles() {
    }

    /** v0.0.11 🍊 Replaces the target so readers see either the old or the new content, never a partial file. */
    static void write(SecureDirectoryStream<Path> directory, Path target, byte[] bytes) throws IOException {
        Path temp = Path.of(FileNames.tempName());
        try {
            try (SeekableByteChannel channel = directory.newByteChannel(temp,
                    Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))) {
                writeFully(channel, bytes);
                force(channel);
            }
            directory.move(temp, directory, target);
        } finally {
            deleteLeftover(directory, temp);
        }
    }

    /** v0.0.11 🍊 Stages the old content plus bytes, then atomically replaces the name without following links. */
    static void append(SecureDirectoryStream<Path> directory, Path target, byte[] bytes) throws IOException {
        Path temp = Path.of(FileNames.tempName());
        try {
            try (SeekableByteChannel staged = directory.newByteChannel(temp,
                    Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))) {
                copyExisting(directory, target, staged);
                writeFully(staged, bytes);
                force(staged);
            }
            directory.move(temp, directory, target);
        } finally {
            deleteLeftover(directory, temp);
        }
    }

    /** v0.0.23 🍊 Path fallback for file systems without no-follow directory handles (macOS): temp + fsync + rename. */
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
            deleteLeftover(target.getParent(), temp);
        }
    }

    /** v0.0.23 🍊 Path fallback append: the open itself never follows a link, so a swapped name cannot be written. */
    static void append(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS)) {
            writeFully(channel, bytes);
            channel.force(true);
        }
    }

    /** v0.0.23 🍊 Removes a path-based temp file after a failed write; the original error still wins. */
    private static void deleteLeftover(Path parent, Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            log.warn("Could not remove temporary workspace file {} in {}: {}", temp.getFileName(), parent,
                    e.getMessage());
        }
    }

    /** v0.0.11 🍊 Writes the whole array (a channel may accept fewer bytes per call). */
    private static void writeFully(SeekableByteChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    /** v0.0.11 🍊 Copies an existing no-follow file into staging; a missing target means append creates it. */
    private static void copyExisting(SecureDirectoryStream<Path> directory, Path target, SeekableByteChannel staged)
            throws IOException {
        try (SeekableByteChannel existing = directory.newByteChannel(target,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            ByteBuffer buffer = ByteBuffer.allocate(32 * 1024);
            while (existing.read(buffer) >= 0) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    staged.write(buffer);
                }
                buffer.clear();
            }
        } catch (NoSuchFileException ignored) {
            // An append to a missing file creates it through the staged replacement.
        }
    }

    /** v0.0.11 🍊 Forces staged bytes to disk; no atomic fallback is permitted when the provider cannot support this. */
    private static void force(SeekableByteChannel channel) throws IOException {
        if (channel instanceof FileChannel file) {
            file.force(true);
            return;
        }
        throw new AtomicMoveNotSupportedException(null, null,
                "The workspace file system cannot fsync an atomic staged replacement.");
    }

    /** v0.0.11 🍊 Removes the temp file after a failed write; a cleanup failure is logged, the original error wins. */
    private static void deleteLeftover(SecureDirectoryStream<Path> directory, Path temp) {
        try {
            directory.deleteFile(temp);
        } catch (NoSuchFileException ignored) {
            // The atomic replacement moved the temp file into place.
        } catch (IOException e) {
            log.warn("Could not remove temporary workspace file {}: {}", temp, e.getMessage());
        }
    }
}
