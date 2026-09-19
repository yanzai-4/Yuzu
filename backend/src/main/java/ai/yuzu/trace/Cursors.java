package ai.yuzu.trace;

import ai.yuzu.common.error.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** v0.0.26 🍊 Opaque history cursors: a page's oldest seq, encoded so clients never build one themselves. */
final class Cursors {

    private static final String PREFIX = "seq:";

    /** v0.0.26 🍊 Static helpers only. */
    private Cursors() {
    }

    /** v0.0.26 🍊 Encodes the seq a client must pass to receive the next (older) page. */
    static String encode(long seq) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((PREFIX + seq).getBytes(StandardCharsets.UTF_8));
    }

    /** v0.0.26 🍊 Decodes a cursor produced by {@link #encode(long)} (BAD_REQUEST when it was not). */
    static long decode(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!decoded.startsWith(PREFIX)) {
                throw new IllegalArgumentException("missing prefix");
            }
            return Long.parseLong(decoded.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw (BadRequestException) new BadRequestException(
                    "The cursor must be one returned by a previous page.").with("cursor", "…");
        }
    }
}
