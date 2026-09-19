package ai.yuzu.workspace;

import java.util.Arrays;
import java.util.Optional;

/** v0.0.11 🍊 The standard top-level folders of every agent workspace and the rules the platform applies to them. */
public enum WorkspaceArea {
    FILES("files", true, true),
    CODE("code", true, true),
    WEB("web", true, true),
    TOOL_OUTPUTS("tool-outputs", false, true),
    LLM("llm", false, false),
    MEMORY("memory", true, true);

    private final String dir;
    private final boolean openForWrites;
    private final boolean metered;

    /** v0.0.11 🍊 Declares a folder, whether generic writes may change it and whether it counts toward the quota. */
    WorkspaceArea(String dir, boolean openForWrites, boolean metered) {
        this.dir = dir;
        this.openForWrites = openForWrites;
        this.metered = metered;
    }

    /** v0.0.11 🍊 Folder name directly under the agent root (for example "tool-outputs"). */
    public String dir() {
        return dir;
    }

    /** v0.0.11 🍊 True when writeText/append/delete may change files here (tool-outputs/ and llm/ are platform-only). */
    public boolean openForWrites() {
        return openForWrites;
    }

    /** v0.0.11 🍊 True when files here count toward the agent's quota (LLM payloads do not). */
    public boolean metered() {
        return metered;
    }

    /** v0.0.11 🍊 Relative workspace path of a file inside this folder (for example "files/notes.md"). */
    public String path(String relative) {
        return relative == null || relative.isBlank() ? dir : dir + "/" + relative;
    }

    /** v0.0.11 🍊 The standard folder a normalized relative path lives in, if any. */
    public static Optional<WorkspaceArea> of(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isEmpty()) {
            return Optional.empty();
        }
        int slash = normalizedPath.indexOf('/');
        String first = slash < 0 ? normalizedPath : normalizedPath.substring(0, slash);
        return Arrays.stream(values()).filter(area -> area.dir.equals(first)).findFirst();
    }
}
