package ai.yuzu.workspace;

import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.SandboxViolationException;
import ai.yuzu.common.error.ToolExecutionException;
import ai.yuzu.common.id.AgentId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.DirectoryStream;
import java.nio.file.NotDirectoryException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

/** v0.0.11 🍊 Keeps every workspace path inside the agent root: relative only, no escapes, control characters or links. */
final class WorkspacePathGuard {

    /** v0.0.11 🍊 Longest accepted path, in characters. */
    static final int MAX_PATH_CHARS = 512;

    /** v0.0.11 🍊 Longest accepted path segment, in UTF-8 bytes (the usual file-system limit). */
    static final int MAX_SEGMENT_BYTES = 255;

    private static final Pattern DRIVE_LETTER = Pattern.compile("^[A-Za-z]:.*", Pattern.DOTALL);
    private static final int MAX_SHOWN_CHARS = 200;

    private final AgentId agentId;
    private final Path root;
    private final Path realRoot;

    /** v0.0.11 🍊 Creates the guard of an existing agent root, which itself must not be a symbolic link. */
    WorkspacePathGuard(AgentId agentId, Path root) throws IOException {
        this.agentId = agentId;
        this.root = root;
        if (Files.isSymbolicLink(root)) {
            throw violation("", "symbolic-link", "The workspace root of " + agentId + " is a symbolic link.");
        }
        this.realRoot = root.toRealPath();
    }

    /** v0.0.11 🍊 Creates one configured/tree directory only after rejecting a pre-existing link or non-directory. */
    static void requireDirectory(AgentId agentId, Path directory, String label) throws IOException {
        BasicFileAttributes attrs = attributesOrNull(directory);
        if (attrs == null) {
            Files.createDirectories(directory);
            attrs = attributesOrNull(directory);
        }
        if (attrs != null && attrs.isSymbolicLink()) {
            throw sandbox(agentId, label, "symbolic-link", "The " + label + " is a symbolic link.");
        }
        if (attrs == null || !attrs.isDirectory()) {
            throw new NotDirectoryException("The " + label + " is not a directory.");
        }
    }

    /** v0.0.11 🍊 Lexically validates a requested path and returns it normalized ("" is the workspace root). */
    String normalize(String requested) {
        String raw = requested == null ? "" : requested;
        if (raw.length() > MAX_PATH_CHARS) {
            throw (BadRequestException) new BadRequestException(
                    "Workspace paths can have at most " + MAX_PATH_CHARS + " characters.")
                    .with("agentId", agentId.value()).with("path", printable(raw)).forAgent(agentId.value());
        }
        if (raw.codePoints().anyMatch(WorkspacePathGuard::forbidden)) {
            throw violation(raw, "control-character", "Workspace paths cannot contain control or invisible characters.");
        }
        if (raw.indexOf('\\') >= 0) {
            throw violation(raw, "backslash", "Workspace paths use '/' separators; backslashes are not allowed.");
        }
        String path = raw.strip();
        if (path.startsWith("/") || Path.of(path).isAbsolute()) {
            throw violation(raw, "absolute-path", "Only paths relative to the workspace are allowed.");
        }
        if (path.startsWith("~") || DRIVE_LETTER.matcher(path).matches()) {
            throw violation(raw, "absolute-path", "Home-directory and drive paths are not allowed; use a workspace path.");
        }
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    throw violation(raw, "parent-escape", "The path leaves the workspace through '..'.");
                }
                segments.removeLast();
                continue;
            }
            if (segment.getBytes(StandardCharsets.UTF_8).length > MAX_SEGMENT_BYTES) {
                throw (BadRequestException) new BadRequestException(
                        "A file or folder name can have at most " + MAX_SEGMENT_BYTES + " bytes.")
                        .with("agentId", agentId.value()).with("path", printable(raw)).forAgent(agentId.value());
            }
            segments.addLast(segment);
        }
        return String.join("/", segments);
    }

    /** v0.0.11 🍊 Normalizes and then checks the file system; returns the absolute path to use. */
    Path resolve(String requested) {
        return check(normalize(requested), requested);
    }

    /** v0.0.11 🍊 File-system checks of a normalized path: no link on any existing component, real path inside the root. */
    Path check(String normalized, String requested) {
        BasicFileAttributes rootAttrs = attributesOrNull(root);
        if (rootAttrs == null || !rootAttrs.isDirectory()) {
            throw new ToolExecutionException("The workspace root changed while it was being checked; please retry.")
                    .with("agentId", agentId.value()).with("path", printable(requested)).forAgent(agentId.value());
        }
        if (rootAttrs.isSymbolicLink()) {
            throw violation(requested, "symbolic-link", "The workspace root of " + agentId + " is a symbolic link.");
        }
        Path deepestExisting = root;
        if (!normalized.isEmpty()) {
            Path current = root;
            for (String segment : normalized.split("/")) {
                current = current.resolve(segment);
                BasicFileAttributes attrs = attributesOrNull(current);
                if (attrs == null) {
                    break;
                }
                if (attrs.isSymbolicLink()) {
                    throw violation(requested, "symbolic-link", "Symbolic links are not allowed in the workspace: "
                            + relative(current) + ".");
                }
                deepestExisting = current;
            }
        }
        try {
            if (!deepestExisting.toRealPath().startsWith(realRoot)) {
                throw violation(requested, "outside-root", "The path resolves outside the workspace.");
            }
        } catch (IOException e) {
            throw (ToolExecutionException) new ToolExecutionException(
                    "The workspace changed while the path was being checked; please retry.", e)
                    .with("agentId", agentId.value()).with("path", printable(requested)).forAgent(agentId.value());
        }
        return normalized.isEmpty() ? root : root.resolve(normalized);
    }

    /** v0.0.11 🍊 Opens an existing target parent by no-follow directory handles, anchored at the workspace root. */
    Optional<SecureDirectoryStream<Path>> openParent(String normalized, String requested) throws IOException {
        Path parent = normalized.isEmpty() ? root : root.resolve(normalized).getParent();
        if (parent == null) {
            throw new IOException("The workspace root has no parent directory.");
        }
        check(normalized, requested);
        DirectoryStream<Path> opened = Files.newDirectoryStream(root);
        if (!(opened instanceof SecureDirectoryStream<Path> current)) {
            opened.close();
            return Optional.empty();
        }
        try {
            Path relativeParent = root.relativize(parent);
            for (Path segment : relativeParent) {
                SecureDirectoryStream<Path> next = current.newDirectoryStream(segment, LinkOption.NOFOLLOW_LINKS);
                current.close();
                current = next;
            }
            return Optional.of(current);
        } catch (IOException | RuntimeException e) {
            current.close();
            throw e;
        }
    }

    /** v0.0.23 🍊 Fallback for file systems without no-follow handles: pins the parent's real path inside the root. */
    Path resolveInsideRoot(String normalized, String requested) throws IOException {
        Path target = check(normalized, requested);
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("The workspace root has no parent directory.");
        }
        Path realParent = parent.toRealPath();
        if (!realParent.startsWith(root.toRealPath())) {
            throw violation(normalized, "symbolic-link",
                    "The folder holding " + requested + " left the workspace while it was being written.");
        }
        return realParent.resolve(target.getFileName());
    }

    /** v0.0.11 🍊 Builds a SANDBOX_VIOLATION carrying the agent, the (printable) requested path and a reason code. */
    SandboxViolationException violation(String requested, String reason, String message) {
        return (SandboxViolationException) new SandboxViolationException(message)
                .with("agentId", agentId.value())
                .with("path", printable(requested))
                .with("reason", reason)
                .forAgent(agentId.value());
    }

    /** v0.0.11 🍊 Attributes of a path without following links, or null when it does not exist (or is unreachable). */
    private static BasicFileAttributes attributesOrNull(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            // Missing, or a parent is not a folder: nothing further down can be a link; the operation reports it.
            return null;
        }
    }

    /** v0.0.11 🍊 A sandbox failure that is available before a guard instance exists. */
    private static SandboxViolationException sandbox(AgentId agentId, String path, String reason, String message) {
        return (SandboxViolationException) new SandboxViolationException(message).with("agentId", agentId.value())
                .with("path", printable(path)).with("reason", reason).forAgent(agentId.value());
    }

    /** v0.0.11 🍊 Workspace-relative display form of an absolute path under the root. */
    private String relative(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    /** v0.0.11 🍊 True for code points that must never appear in a path (controls, bidi/format, separators, lone surrogates). */
    private static boolean forbidden(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR
                || type == Character.SURROGATE;
    }

    /** v0.0.11 🍊 Escapes invisible characters and shortens the path so it is safe to show in errors and logs. */
    static String printable(String requested) {
        if (requested == null) {
            return "";
        }
        StringBuilder shown = new StringBuilder();
        requested.codePoints().limit(MAX_SHOWN_CHARS).forEach(cp -> {
            if (forbidden(cp)) {
                shown.append(String.format("\\u%04X", cp));
            } else {
                shown.appendCodePoint(cp);
            }
        });
        if (requested.codePointCount(0, requested.length()) > MAX_SHOWN_CHARS) {
            shown.append('…');
        }
        return shown.toString();
    }
}
