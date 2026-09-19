package ai.yuzu.workspace;

import java.time.Instant;

/** v0.0.11 🍊 One file or folder of a workspace (path relative to the agent root, "/" separators). */
public record WorkspaceEntry(String path, String name, Kind kind, long sizeBytes, Instant modifiedAt) {

    /** v0.0.11 🍊 Kind of file-system object; links and special files are listed but never opened. */
    public enum Kind { FILE, DIRECTORY, SYMLINK, OTHER }

    /** v0.0.11 🍊 True for regular files. */
    public boolean isFile() {
        return kind == Kind.FILE;
    }

    /** v0.0.11 🍊 True for folders. */
    public boolean isDirectory() {
        return kind == Kind.DIRECTORY;
    }
}
