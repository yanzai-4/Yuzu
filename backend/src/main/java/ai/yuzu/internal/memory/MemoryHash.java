package ai.yuzu.internal.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** v0.0.28 🍊 SHA-256 of a candidate's normalized text; backs the {@code UNIQUE(agent_id, content_hash)} keys. */
public final class MemoryHash {

    /** v0.0.28 🍊 Static helpers only. */
    private MemoryHash() {
    }

    /** v0.0.28 🍊 32 bytes identifying the candidate's content regardless of case and whitespace. */
    public static byte[] of(MemoryCandidate candidate) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(candidate.hashSource().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
