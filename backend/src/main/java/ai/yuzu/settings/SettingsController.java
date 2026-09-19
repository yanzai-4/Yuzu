package ai.yuzu.settings;

import ai.yuzu.llm.ModelTier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** v0.0.7 🍊 Console endpoints: model settings, API key and the provider's model list. */
@RestController
@RequestMapping("/api/settings/llm")
public class SettingsController {

    private final SettingsService settings;

    /** v0.0.7 🍊 Injects the settings service. */
    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    /** v0.0.7 🍊 Current settings with a masked key. */
    @GetMapping
    public LlmSettingsView get() {
        return settings.view();
    }

    /** v0.0.7 🍊 Saves provider, base URL and tier models. */
    @PutMapping
    public LlmSettingsView update(@Valid @RequestBody UpdateRequest request) {
        return settings.update(request.provider(), request.baseUrl(), request.tiers());
    }

    /** v0.0.7 🍊 Saves (encrypted) the API key of the current provider. */
    @PutMapping("/key")
    public LlmSettingsView saveKey(@Valid @RequestBody KeyRequest request) {
        return settings.saveApiKey(request.apiKey());
    }

    /** v0.0.7 🍊 Model ids available at the provider. */
    @GetMapping("/models")
    public List<String> models() {
        return settings.listModels();
    }

    /** v0.0.7 🍊 Body of PUT /api/settings/llm. */
    public record UpdateRequest(@NotNull LlmProvider provider, String baseUrl, Map<ModelTier, TierSettings> tiers) {
    }

    /** v0.0.7 🍊 Body of PUT /api/settings/llm/key. */
    public record KeyRequest(@NotBlank String apiKey) {
    }
}
