package ai.yuzu.workspace;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

/** v0.0.11 🍊 Cached size of the metered part of a workspace (everything except llm/), recomputed by a file walk. */
final class WorkspaceUsage {

    private static final long MAX_AGE_NANOS = TimeUnit.SECONDS.toNanos(60);

    private final Path root;
    private final Path unmetered;
    private volatile long computedAtNanos;
    private volatile long bytes = -1;

    /** v0.0.11 🍊 Creates the cache for an agent root; nothing is computed until first use. */
    WorkspaceUsage(Path root) {
        this.root = root;
        this.unmetered = root.resolve(WorkspaceArea.LLM.dir());
    }

    /** v0.0.11 🍊 The cached value while it is fresh (under 60 s old), otherwise -1. */
    long cached() {
        long value = bytes;
        return value >= 0 && System.nanoTime() - computedAtNanos < MAX_AGE_NANOS ? value : -1;
    }

    /** v0.0.11 🍊 Current usage, recomputed when stale; callers hold the workspace write lock. */
    long current() throws IOException {
        long value = cached();
        if (value >= 0) {
            return value;
        }
        long walked = walk();
        computedAtNanos = System.nanoTime();
        bytes = walked;
        return walked;
    }

    /** v0.0.11 🍊 Forgets the cached value (called after every write). */
    void invalidate() {
        bytes = -1;
    }

    /** v0.0.11 🍊 Sums regular-file sizes without following links, skipping the unmetered llm/ folder. */
    private long walk() throws IOException {
        SizeVisitor visitor = new SizeVisitor(unmetered);
        Files.walkFileTree(root, visitor);
        return visitor.total;
    }

    /** v0.0.11 🍊 File visitor that adds up regular-file sizes. */
    private static final class SizeVisitor extends SimpleFileVisitor<Path> {

        private final Path skipped;
        private long total;

        /** v0.0.11 🍊 Creates a visitor that skips one subtree. */
        private SizeVisitor(Path skipped) {
            this.skipped = skipped;
        }

        /** v0.0.11 🍊 Skips the unmetered folder. */
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            return dir.equals(skipped) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
        }

        /** v0.0.11 🍊 Counts regular files only (links and special files have no metered size). */
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (attrs.isRegularFile()) {
                total += attrs.size();
            }
            return FileVisitResult.CONTINUE;
        }

        /** v0.0.11 🍊 A file removed while walking simply no longer counts. */
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            return FileVisitResult.CONTINUE;
        }
    }
}
