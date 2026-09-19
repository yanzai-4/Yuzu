package ai.yuzu.settings;

import ai.yuzu.llm.ModelTier;

import java.util.EnumMap;
import java.util.Map;

/**
 * v0.0.7 🍊 Persisted model settings: provider, base URL and one {@link TierSettings} per tier.
 *
 * <p>The API key is stored separately (encrypted, per provider) and is never part of this record.</p>
 */
public record LlmSettings(LlmProvider provider, String baseUrl, Map<ModelTier, TierSettings> tiers) {

    /** v0.0.7 🍊 Fills missing tiers with defaults and trims the base URL. */
    public LlmSettings {
        provider = provider == null ? LlmProvider.OPENAI : provider;
        baseUrl = (baseUrl == null || baseUrl.isBlank() ? provider.defaultBaseUrl() : baseUrl.strip())
                .replaceAll("/+$", "");
        EnumMap<ModelTier, TierSettings> complete = new EnumMap<>(ModelTier.class);
        for (ModelTier tier : ModelTier.values()) {
            TierSettings value = tiers == null ? null : tiers.get(tier);
            complete.put(tier, value == null || value.model().isEmpty() ? DEFAULT_TIERS.get(tier) : value);
        }
        tiers = Map.copyOf(complete);
    }

    private static final Map<ModelTier, TierSettings> DEFAULT_TIERS = Map.of(
            ModelTier.IMPORTANT, new TierSettings("gpt-5.5", "low", 4_000),
            ModelTier.DEFAULT, new TierSettings("gpt-5-mini", "minimal", 2_000),
            ModelTier.LIGHT, new TierSettings("gpt-5-nano", "minimal", 600));

    /** v0.0.7 🍊 Out-of-the-box settings (OpenAI with GPT-5 family models; editable in the console). */
    public static LlmSettings defaults() {
        return new LlmSettings(LlmProvider.OPENAI, LlmProvider.OPENAI.defaultBaseUrl(), DEFAULT_TIERS);
    }

    /** v0.0.7 🍊 Settings of one tier. */
    public TierSettings tier(ModelTier tier) {
        return tiers.get(tier);
    }
}
