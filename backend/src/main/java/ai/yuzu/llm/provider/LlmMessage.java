package ai.yuzu.llm.provider;

/**
 * v0.0.8 🍊 One Chat Completions message.
 *
 * @param role    "system", "user" or "assistant"
 * @param content text content
 */
public record LlmMessage(String role, String content) {

    /** v0.0.8 🍊 System message. */
    public static LlmMessage system(String content) {
        return new LlmMessage("system", content);
    }

    /** v0.0.8 🍊 User message. */
    public static LlmMessage user(String content) {
        return new LlmMessage("user", content);
    }

    /** v0.0.8 🍊 Assistant message (used to replay an invalid answer before retry feedback). */
    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content);
    }
}
