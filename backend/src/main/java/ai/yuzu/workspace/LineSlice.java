package ai.yuzu.workspace;

import java.util.List;

/** v0.0.11 🍊 Lines fromLine..toLine (1-based, inclusive); hasMore = more lines follow, truncated = budget hit or line cut. */
public record LineSlice(String path, long fromLine, long toLine, List<String> lines, boolean hasMore,
                        boolean truncated) {

    /** v0.0.11 🍊 Copies the lines so the slice is immutable. */
    public LineSlice {
        lines = List.copyOf(lines);
    }

    /** v0.0.11 🍊 True when no line was returned (for example fromLine is past the end of the file). */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** v0.0.11 🍊 The lines prefixed with their numbers ("12| text"), ready to show to an agent. */
    public String numbered() {
        StringBuilder text = new StringBuilder();
        long number = fromLine;
        for (String line : lines) {
            text.append(number++).append("| ").append(line).append('\n');
        }
        return text.toString();
    }
}
