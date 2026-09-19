package ai.yuzu.settings;

import ai.yuzu.llm.ModelTier;

import java.util.Map;

/**
 * v0.0.7 🍊 Settings as shown in the console (contract type {@code LlmSettingsView}); the key is only masked.
 */
public record LlmSettingsView(LlmProvider provider, String baseUrl, boolean hasKey, String apiKeyMasked,
                              Map<ModelTier, TierSettings> tiers) {
}
