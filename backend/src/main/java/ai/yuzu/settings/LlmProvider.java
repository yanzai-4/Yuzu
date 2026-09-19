package ai.yuzu.settings;

/** v0.0.7 🍊 Model provider presets selectable in the console (all speak the OpenAI Chat Completions API). */
public enum LlmProvider {
    OPENAI("https://api.openai.com/v1"),
    EDGEONE("https://ai-gateway.edgeone.link/v1"),
    CUSTOM("");

    private final String defaultBaseUrl;

    LlmProvider(String defaultBaseUrl) {
        this.defaultBaseUrl = defaultBaseUrl;
    }

    /** v0.0.7 🍊 Base URL suggested for this provider (empty for CUSTOM). */
    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }
}
