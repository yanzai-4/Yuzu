package ai.yuzu.llm.prompt;

import ai.yuzu.llm.provider.LlmMessage;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * v0.0.9 🍊 Local token counting (o200k_base) for context budgets and for providers that report no usage.
 *
 * <p>Never used for billing: real usage always comes from the provider when available.</p>
 */
@Component
public class TokenEstimator {

    private static final int MESSAGE_OVERHEAD = 4;
    private final Encoding encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.O200K_BASE);

    /** v0.0.9 🍊 Tokens in a text. */
    public int count(String text) {
        return text == null || text.isEmpty() ? 0 : encoding.countTokens(text);
    }

    /** v0.0.9 🍊 Tokens of a message list including per-message overhead. */
    public int count(List<LlmMessage> messages) {
        int total = 3;
        for (LlmMessage m : messages) {
            total += MESSAGE_OVERHEAD + count(m.content());
        }
        return total;
    }

    /** v0.0.9 🍊 Keeps the END of a text within a token budget (recent content matters most). */
    public String keepTail(String text, int maxTokens) {
        if (count(text) <= maxTokens) {
            return text;
        }
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (count(text.substring(mid)) > maxTokens) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return "…" + text.substring(lo);
    }

    /** v0.0.9 🍊 Keeps the START of a text within a token budget. */
    public String keepHead(String text, int maxTokens) {
        if (count(text) <= maxTokens) {
            return text;
        }
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (count(text.substring(0, mid)) > maxTokens) {
                hi = mid - 1;
            } else {
                lo = mid;
            }
        }
        return text.substring(0, lo) + "…";
    }
}
