package ai.yuzu.workspace;

/** v0.0.11 🍊 A byte range of a file decoded as UTF-8, cut only at character boundaries; continue from nextOffsetBytes. */
public record TextChunk(String path, String text, long offsetBytes, long nextOffsetBytes, long fileSizeBytes,
                        boolean eof, boolean binary) {

    /** v0.0.11 🍊 True when the file continues after this chunk. */
    public boolean truncated() {
        return !eof;
    }
}
