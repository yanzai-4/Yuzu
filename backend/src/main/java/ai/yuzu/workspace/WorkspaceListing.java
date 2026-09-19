package ai.yuzu.workspace;

import java.util.List;

/** v0.0.11 🍊 Contents of one workspace folder: sorted entries (folders first), the total count and a truncation flag. */
public record WorkspaceListing(String path, List<WorkspaceEntry> entries, int totalEntries, boolean truncated) {

    /** v0.0.11 🍊 Copies the entries so the listing is immutable. */
    public WorkspaceListing {
        entries = List.copyOf(entries);
    }
}
