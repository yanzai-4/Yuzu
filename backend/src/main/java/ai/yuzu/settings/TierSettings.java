package ai.yuzu.settings;

/**
 * v0.0.7 🍊 Model configuration of one tier.
 *
 * @param model           model name sent to the provider (for example gpt-5-mini or @makers/deepseek-v4-flash)
 * @param reasoningEffort optional reasoning effort for reasoning models (none/minimal/low/medium/high)
 * @param maxOutputTokens maximum visible output tokens per call
 */
public record TierSettings(String model, String reasoningEffort, int maxOutputTokens) {

    /** v0.0.7 🍊 Normalizes blank effort to null and clamps the token budget. */
    public TierSettings {
        model = model == null ? "" : model.strip();
        reasoningEffort = reasoningEffort == null || reasoningEffort.isBlank() ? null : reasoningEffort.strip();
        maxOutputTokens = Math.max(64, Math.min(maxOutputTokens, 32_000));
    }
}
