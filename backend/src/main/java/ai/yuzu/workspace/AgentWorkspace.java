package ai.yuzu.workspace;

import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.error.ToolExecutionException;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/** v0.0.11 🍊 One agent's sandboxed workspace: guarded paths, streaming reads, atomic writes, quota and spill files. */
public final class AgentWorkspace {

    /** v0.0.11 🍊 Largest content accepted by one writeText or append call (1 MB). */
    public static final int MAX_WRITE_BYTES = 1 << 20;

    /** v0.0.11 🍊 Largest tool output or LLM payload the platform saves (64 MB). */
    public static final int MAX_SYSTEM_FILE_BYTES = 64 << 20;

    /** v0.0.11 🍊 Most bytes returned by one readText/readChunk call (4 MB). */
    public static final int MAX_READ_BYTES = 4 << 20;

    /** v0.0.11 🍊 Tool results larger than this (16 KB) are saved with saveToolOutput instead of inlined. */
    public static final int INLINE_RESULT_LIMIT_BYTES = 16 << 10;

    /** v0.0.11 🍊 Most lines returned by one readLines call. */
    public static final int MAX_LINES_PER_READ = 5_000;

    /** v0.0.11 🍊 Most bytes of line text returned by one readLines call (1 MB). */
    public static final int MAX_SLICE_BYTES = 1 << 20;

    /** v0.0.11 🍊 Longest line returned in full by readLines (16 KB); longer lines are cut with a marker. */
    public static final int MAX_LINE_BYTES = 16 << 10;

    /** v0.0.11 🍊 Most entries returned by one listing. */
    public static final int MAX_LIST_ENTRIES = 1_000;

    private static final int MIN_CHUNK_BYTES = 16;
    private static final int MAX_COLLECTED_ENTRIES = 5_000;
    private static final int NAME_ATTEMPTS = 3;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HHmmss", Locale.ROOT);
    private static final LargeFileReader.Budget LINE_BUDGET =
            new LargeFileReader.Budget(MAX_LINES_PER_READ, MAX_SLICE_BYTES, MAX_LINE_BYTES);
    private static final Comparator<WorkspaceEntry> FOLDERS_FIRST = Comparator
            .comparing((WorkspaceEntry entry) -> !entry.isDirectory())
            .thenComparing(WorkspaceEntry::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(WorkspaceEntry::name);

    private final AgentId agentId;
    private final Path root;
    private final WorkspacePathGuard guard;
    private final WorkspaceUsage usage;
    private final WorkspaceQuota quota;
    private final NaturalTime time;
    private final ReentrantLock writeLock = new ReentrantLock();

    /** v0.0.11 🍊 Opens the workspace of an agent whose root folder already exists. */
    AgentWorkspace(AgentId agentId, Path root, WorkspaceQuota quota, NaturalTime time) throws IOException {
        this.agentId = agentId;
        this.root = root;
        this.guard = new WorkspacePathGuard(agentId, root);
        this.usage = new WorkspaceUsage(root);
        this.quota = quota;
        this.time = time;
    }

    /** v0.0.11 🍊 The owning agent. */
    public AgentId agentId() {
        return agentId;
    }

    /** v0.0.11 🍊 Absolute root folder (for platform code such as the code sandbox; never show it to an agent). */
    public Path root() {
        return root;
    }

    /** v0.0.11 🍊 Normalizes a relative path after the lexical sandbox checks ("" is the root). */
    public String normalize(String path) {
        return guard.normalize(path);
    }

    /** v0.0.11 🍊 Absolute path of a relative workspace path after every sandbox check (links, escapes, controls). */
    public Path resolve(String path) {
        return guard.resolve(path);
    }

    /** v0.0.11 🍊 True when something exists at the path. */
    public boolean exists(String path) {
        String rel = guard.normalize(path);
        return attributesOrNull(guard.check(rel, path)) != null;
    }

    /** v0.0.11 🍊 Kind, size and modification time of a file or folder. */
    public WorkspaceEntry stat(String path) {
        String rel = guard.normalize(path);
        Path target = guard.check(rel, path);
        try {
            return entry(rel, target);
        } catch (IOException e) {
            throw ioFailure("stat", rel, e);
        }
    }

    /** v0.0.11 🍊 Lists a folder, folders first then by name; "" lists the workspace root. */
    public WorkspaceListing list(String dir) {
        String rel = guard.normalize(dir);
        Path target = guard.check(rel, dir);
        BasicFileAttributes attrs = attributesOrNull(target);
        if (attrs == null) {
            throw notFound(rel);
        }
        if (!attrs.isDirectory()) {
            throw badRequest(rel, display(rel) + " is a file, not a folder.");
        }
        List<WorkspaceEntry> entries = new ArrayList<>();
        int total = 0;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(target)) {
            for (Path child : children) {
                String name = WorkspacePathGuard.printable(child.getFileName().toString());
                if (FileNames.isTemp(name)) {
                    continue;
                }
                total++;
                if (entries.size() < MAX_COLLECTED_ENTRIES) {
                    entryIfPresent(join(rel, name), child).ifPresent(entries::add);
                }
            }
        } catch (IOException e) {
            throw ioFailure("list", rel, e);
        } catch (DirectoryIteratorException e) {
            throw ioFailure("list", rel, e.getCause());
        }
        entries.sort(FOLDERS_FIRST);
        List<WorkspaceEntry> shown = entries.subList(0, Math.min(entries.size(), MAX_LIST_ENTRIES));
        return new WorkspaceListing(rel, shown, total, total > shown.size());
    }

    /** v0.0.11 🍊 The first maxBytes (16 B..4 MB) of a text file; truncated() tells whether more follows. */
    public TextChunk readText(String path, int maxBytes) {
        return readChunk(path, 0, maxBytes);
    }

    /** v0.0.11 🍊 Reads lengthBytes (16 B..4 MB) from offsetBytes, aligned to UTF-8 boundaries; resume at nextOffsetBytes. */
    public TextChunk readChunk(String path, long offsetBytes, int lengthBytes) {
        String rel = guard.normalize(path);
        if (offsetBytes < 0) {
            throw badRequest(rel, "The offset cannot be negative.");
        }
        int length = Math.max(MIN_CHUNK_BYTES, Math.min(lengthBytes, MAX_READ_BYTES));
        Path file = requireReadableFile(rel, path);
        try {
            return LargeFileReader.readChunk(rel, file, offsetBytes, length);
        } catch (IOException e) {
            throw ioFailure("read", rel, e);
        }
    }

    /** v0.0.11 🍊 Streams lines fromLine..toLine (1-based, inclusive) without loading the file; budgets cap the result. */
    public LineSlice readLines(String path, long fromLine, long toLine) {
        String rel = guard.normalize(path);
        if (fromLine < 1) {
            throw badRequest(rel, "Line numbers start at 1.");
        }
        if (toLine < fromLine) {
            throw badRequest(rel, "toLine must not be smaller than fromLine.");
        }
        Path file = requireReadableFile(rel, path);
        try {
            return LargeFileReader.readLines(rel, file, fromLine, toLine, LINE_BUDGET);
        } catch (IOException e) {
            throw ioFailure("read", rel, e);
        }
    }

    /** v0.0.11 🍊 Number of lines of a file, counted by streaming (a last line without newline counts). */
    public long countLines(String path) {
        String rel = guard.normalize(path);
        Path file = requireReadableFile(rel, path);
        try {
            return LargeFileReader.countLines(file);
        } catch (IOException e) {
            throw ioFailure("read", rel, e);
        }
    }

    /** v0.0.11 🍊 Atomically creates or replaces a text file (at most 1 MB, inside files/, code/, web/ or memory/). */
    public WorkspaceEntry writeText(String path, String content) {
        byte[] bytes = utf8(content);
        String rel = guard.normalize(path);
        requireWritable(rel, path);
        requireSize(rel, bytes.length, MAX_WRITE_BYTES, "One write");
        writeLock.lock();
        try {
            Path target = prepareParent(rel, path);
            long previous = replaceableSize(rel, path, target);
            reserve(rel, bytes.length - previous);
            AtomicFiles.write(target, bytes);
            usage.invalidate();
            return entry(rel, target);
        } catch (IOException e) {
            throw ioFailure("write", rel, e);
        } finally {
            writeLock.unlock();
        }
    }

    /** v0.0.11 🍊 Appends text (at most 1 MB per call) to a file, creating it when missing; the file grows up to the quota. */
    public WorkspaceEntry append(String path, String content) {
        byte[] bytes = utf8(content);
        String rel = guard.normalize(path);
        requireWritable(rel, path);
        requireSize(rel, bytes.length, MAX_WRITE_BYTES, "One append");
        writeLock.lock();
        try {
            Path target = prepareParent(rel, path);
            if (attributesOrNull(target) != null) {
                replaceableSize(rel, path, target);
                if (linkCount(target) > 1) {
                    throw guard.violation(path, "hard-link", "Files with several hard links cannot be changed: "
                            + rel + ".");
                }
            }
            reserve(rel, bytes.length);
            AtomicFiles.append(target, bytes);
            usage.invalidate();
            return entry(rel, target);
        } catch (IOException e) {
            throw ioFailure("append", rel, e);
        } finally {
            writeLock.unlock();
        }
    }

    /** v0.0.11 🍊 Deletes a file or an empty folder in a writable area; false when nothing was there. */
    public boolean delete(String path) {
        String rel = guard.normalize(path);
        requireWritable(rel, path);
        writeLock.lock();
        try {
            Path target = guard.check(rel, path);
            if (attributesOrNull(target) == null) {
                return false;
            }
            Files.delete(target);
            usage.invalidate();
            return true;
        } catch (IOException e) {
            throw ioFailure("delete", rel, e);
        } finally {
            writeLock.unlock();
        }
    }

    /** v0.0.11 🍊 Saves a large tool result under tool-outputs/<day>/ (metered) and returns its relative path. */
    public String saveToolOutput(String name, String content) {
        byte[] bytes = utf8(content);
        String prefix = WorkspaceArea.TOOL_OUTPUTS.dir() + "/";
        requireSize(prefix, bytes.length, MAX_SYSTEM_FILE_BYTES, "A tool output");
        writeLock.lock();
        String rel = prefix;
        try {
            LocalDateTime now = localNow();
            Path target = null;
            for (int attempt = 0; attempt < NAME_ATTEMPTS && target == null; attempt++) {
                rel = prefix + DAY.format(now) + "/" + CLOCK.format(now) + "-" + FileNames.slug(name, "output") + "-"
                        + FileNames.randomSuffix() + ".txt";
                Path candidate = prepareParent(rel, rel);
                if (attributesOrNull(candidate) == null) {
                    target = candidate;
                }
            }
            if (target == null) {
                throw new ToolExecutionException("Could not find a free file name for the tool output.");
            }
            reserve(rel, bytes.length);
            AtomicFiles.write(target, bytes);
            usage.invalidate();
            return rel;
        } catch (IOException e) {
            throw ioFailure("write", rel, e);
        } finally {
            writeLock.unlock();
        }
    }

    /** v0.0.11 🍊 Saves the result only when it is larger than 16 KB; returns the saved path, or empty to inline it. */
    public Optional<String> saveToolOutputIfLarge(String name, String content) {
        if (content == null || utf8(content).length <= INLINE_RESULT_LIMIT_BYTES) {
            return Optional.empty();
        }
        return Optional.of(saveToolOutput(name, content));
    }

    /** v0.0.11 🍊 Saves an LLM request/response payload as llm/<day>/<callId>.json (not metered) and returns its path. */
    public String saveLlmPayload(String callId, String json) {
        byte[] bytes = utf8(json);
        String rel = WorkspaceArea.LLM.dir() + "/" + DAY.format(localNow()) + "/" + FileNames.slug(callId, "call")
                + ".json";
        requireSize(rel, bytes.length, MAX_SYSTEM_FILE_BYTES, "An LLM payload");
        writeLock.lock();
        try {
            Path target = prepareParent(rel, rel);
            replaceableSize(rel, rel, target);
            AtomicFiles.write(target, bytes);
            return rel;
        } catch (IOException e) {
            throw ioFailure("write", rel, e);
        } finally {
            writeLock.unlock();
        }
    }

    /** v0.0.11 🍊 Bytes counted toward the quota (all folders except llm/); cached until the next write. */
    public long usedBytes() {
        long cached = usage.cached();
        if (cached >= 0) {
            return cached;
        }
        writeLock.lock();
        try {
            return usage.current();
        } catch (IOException e) {
            throw ioFailure("measure", "", e);
        } finally {
            writeLock.unlock();
        }
    }

    /** v0.0.11 🍊 The agent's current quota in bytes (from its limits). */
    public long quotaBytes() {
        return quota.quotaBytes(agentId);
    }

    /** v0.0.11 🍊 Forgets the cached usage (call after something outside this API, such as the sandbox, wrote files). */
    public void invalidateUsage() {
        writeLock.lock();
        try {
            usage.invalidate();
        } finally {
            writeLock.unlock();
        }
    }

    /** v0.0.11 🍊 Recreates the folder tree if the root disappeared (called on every workspace lookup). */
    void ensureTree() throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            createTree(root);
        }
    }

    /** v0.0.11 🍊 Creates the agent root and every standard folder (idempotent). */
    static void createTree(Path root) throws IOException {
        Files.createDirectories(root);
        for (WorkspaceArea area : WorkspaceArea.values()) {
            Files.createDirectories(root.resolve(area.dir()));
        }
    }

    /** v0.0.11 🍊 Checks the path, creates missing parent folders, then checks again (nothing became a link meanwhile). */
    private Path prepareParent(String rel, String requested) throws IOException {
        Path target = guard.check(rel, requested);
        Files.createDirectories(target.getParent());
        return guard.check(rel, requested);
    }

    /** v0.0.11 🍊 Size of an existing regular file that is about to be replaced (0 when missing). */
    private long replaceableSize(String rel, String requested, Path target) {
        BasicFileAttributes attrs = attributesOrNull(target);
        if (attrs == null) {
            return 0;
        }
        if (attrs.isDirectory()) {
            throw badRequest(rel, rel + " is a folder, not a file.");
        }
        if (!attrs.isRegularFile()) {
            throw guard.violation(requested, "special-file", "Only regular files can be changed: " + rel + ".");
        }
        return attrs.size();
    }

    /** v0.0.11 🍊 Opens only existing regular files with a single hard link (no folders, devices, FIFOs or aliases). */
    private Path requireReadableFile(String rel, String requested) {
        if (rel.isEmpty()) {
            throw badRequest(rel, "A file path is required.");
        }
        Path file = guard.check(rel, requested);
        BasicFileAttributes attrs = attributesOrNull(file);
        if (attrs == null) {
            throw notFound(rel);
        }
        if (attrs.isDirectory()) {
            throw badRequest(rel, rel + " is a folder, not a file.");
        }
        if (!attrs.isRegularFile()) {
            throw guard.violation(requested, "special-file", "Only regular files can be opened: " + rel + ".");
        }
        if (linkCount(file) > 1) {
            throw guard.violation(requested, "hard-link", "Files with several hard links cannot be opened: "
                    + rel + ".");
        }
        return file;
    }

    /** v0.0.11 🍊 Writes must target a file inside a folder that is open for writes (not tool-outputs/ or llm/). */
    private void requireWritable(String rel, String requested) {
        if (rel.isEmpty()) {
            throw badRequest(rel, "A file path is required.");
        }
        WorkspaceArea area = WorkspaceArea.of(rel).orElseThrow(() -> denied(rel, "outside-areas",
                "Files can only be written inside " + writableAreas() + " (for example files/notes.md)."));
        if (!area.openForWrites()) {
            throw denied(rel, "read-only-area", area.dir() + "/ is managed by the platform and is read-only.");
        }
        if (rel.equals(area.dir())) {
            throw badRequest(rel, rel + " is a folder, not a file.");
        }
    }

    /** v0.0.11 🍊 Throws PERMISSION_DENIED (reason "quota") when growing by delta bytes would exceed the quota. */
    private void reserve(String rel, long delta) throws IOException {
        if (delta <= 0) {
            return;
        }
        long used = usage.current();
        long limit = quotaBytes();
        if (used + delta > limit) {
            long free = Math.max(0, limit - used);
            throw (PermissionDeniedException) new PermissionDeniedException("Workspace quota exceeded: writing " + rel
                    + " needs " + FileNames.humanBytes(delta) + " more, but only " + FileNames.humanBytes(free)
                    + " of the " + FileNames.humanBytes(limit) + " quota is free.")
                    .with("reason", "quota").with("agentId", agentId.value()).with("path", rel)
                    .with("usedBytes", used).with("quotaBytes", limit).with("requestedBytes", delta)
                    .forAgent(agentId.value());
        }
    }

    /** v0.0.11 🍊 Rejects content above a size limit with BAD_REQUEST. */
    private void requireSize(String rel, long size, long limit, String what) {
        if (size > limit) {
            throw (BadRequestException) new BadRequestException(what + " can be at most " + FileNames.humanBytes(limit)
                    + " (this one is " + FileNames.humanBytes(size) + ").")
                    .with("agentId", agentId.value()).with("path", rel).with("limitBytes", limit)
                    .with("requestedBytes", size).forAgent(agentId.value());
        }
    }

    /** v0.0.11 🍊 Entry for a path, reading attributes without following links. */
    private static WorkspaceEntry entry(String rel, Path path) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        WorkspaceEntry.Kind kind = attrs.isSymbolicLink() ? WorkspaceEntry.Kind.SYMLINK
                : attrs.isDirectory() ? WorkspaceEntry.Kind.DIRECTORY
                : attrs.isRegularFile() ? WorkspaceEntry.Kind.FILE
                : WorkspaceEntry.Kind.OTHER;
        return new WorkspaceEntry(rel, rel.substring(rel.lastIndexOf('/') + 1), kind,
                kind == WorkspaceEntry.Kind.FILE ? attrs.size() : 0,
                attrs.lastModifiedTime().toInstant().truncatedTo(ChronoUnit.MILLIS));
    }

    /** v0.0.11 🍊 Entry for a listed child, or empty when it vanished while listing. */
    private static Optional<WorkspaceEntry> entryIfPresent(String rel, Path path) {
        try {
            return Optional.of(entry(rel, path));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** v0.0.11 🍊 Attributes without following links, or null when the path does not exist. */
    private static BasicFileAttributes attributesOrNull(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            return null;
        }
    }

    /** v0.0.11 🍊 Number of hard links of a file (1 when the platform cannot tell). */
    private static int linkCount(Path file) {
        try {
            return Files.getAttribute(file, "unix:nlink", LinkOption.NOFOLLOW_LINKS) instanceof Integer count
                    ? count : 1;
        } catch (UnsupportedOperationException | IllegalArgumentException | IOException e) {
            return 1;
        }
    }

    /** v0.0.11 🍊 Maps an I/O failure to a specific YuzuException without leaking absolute server paths. */
    private YuzuException ioFailure(String operation, String rel, IOException e) {
        String reason = e instanceof FileSystemException fs && fs.getReason() != null ? fs.getReason() : "";
        String lower = reason.toLowerCase(Locale.ROOT);
        YuzuException error;
        if (e instanceof NoSuchFileException) {
            error = new NotFoundException("Nothing exists at " + display(rel) + " in the workspace.");
        } else if (lower.contains("symbolic link")) {
            return guard.violation(rel, "symbolic-link", "Symbolic links are not allowed in the workspace: "
                    + display(rel) + ".");
        } else if (e instanceof NotDirectoryException || e instanceof FileAlreadyExistsException
                || lower.contains("not a directory")) {
            error = new BadRequestException("Part of " + display(rel) + " is a file, not a folder.");
        } else if (e instanceof DirectoryNotEmptyException) {
            error = new BadRequestException(display(rel) + " is a folder that is not empty.");
        } else if (e instanceof AccessDeniedException) {
            error = new ToolExecutionException("The operating system denied " + operation + " access to "
                    + display(rel) + ".", e);
        } else {
            error = new ToolExecutionException("Workspace " + operation + " failed for " + display(rel) + ": "
                    + (reason.isEmpty() ? e.getClass().getSimpleName() : reason), e);
        }
        return error.with("agentId", agentId.value()).with("path", rel).forAgent(agentId.value());
    }

    /** v0.0.11 🍊 NOT_FOUND for a workspace path. */
    private NotFoundException notFound(String rel) {
        return (NotFoundException) new NotFoundException("Nothing exists at " + display(rel) + " in the workspace.")
                .with("agentId", agentId.value()).with("path", rel).forAgent(agentId.value());
    }

    /** v0.0.11 🍊 BAD_REQUEST for a workspace path. */
    private BadRequestException badRequest(String rel, String message) {
        return (BadRequestException) new BadRequestException(message)
                .with("agentId", agentId.value()).with("path", rel).forAgent(agentId.value());
    }

    /** v0.0.11 🍊 PERMISSION_DENIED for a workspace path with a reason code. */
    private PermissionDeniedException denied(String rel, String reason, String message) {
        return (PermissionDeniedException) new PermissionDeniedException(message)
                .with("agentId", agentId.value()).with("path", rel).with("reason", reason).forAgent(agentId.value());
    }

    /** v0.0.11 🍊 Current local date-time in the workgroup zone (used for dated folders). */
    private LocalDateTime localNow() {
        return LocalDateTime.ofInstant(time.nowInstant(), time.zone());
    }

    /** v0.0.11 🍊 "files/, code/, web/, memory/" — the folders open for generic writes. */
    private static String writableAreas() {
        return Arrays.stream(WorkspaceArea.values()).filter(WorkspaceArea::openForWrites)
                .map(area -> area.dir() + "/").collect(Collectors.joining(", "));
    }

    /** v0.0.11 🍊 Human wording of a relative path ("the workspace root" for ""). */
    private static String display(String rel) {
        return rel.isEmpty() ? "the workspace root" : rel;
    }

    /** v0.0.11 🍊 Joins a folder path and a child name. */
    private static String join(String dir, String name) {
        return dir.isEmpty() ? name : dir + "/" + name;
    }

    /** v0.0.11 🍊 UTF-8 bytes of a string (null is empty). */
    private static byte[] utf8(String content) {
        return content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
    }
}
