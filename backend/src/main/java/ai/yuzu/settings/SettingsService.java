package ai.yuzu.settings;

import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.LlmAuthException;
import ai.yuzu.common.error.LlmTransportException;
import ai.yuzu.common.error.NotConfiguredException;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.ModelTier;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * v0.0.7 🍊 Console settings: provider, base URL, tier models and the encrypted API key (one per provider).
 *
 * <p>Settings are read by every LLM call, so the decoded settings and key are held in memory and
 * refreshed on every write. Listeners (for example the LLM client) observe {@link #revision()} to
 * rebuild their HTTP clients when the provider changes.</p>
 */
@Service
public class SettingsService {

    private static final String SETTINGS_KEY = "llm.settings";
    private static final String KEY_PREFIX = "llm.key.";

    private final AppSettingRepository store;
    private final SecretVault vault;
    private final Jsons jsons;
    private final SseHub hub;
    private final NaturalTime time;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final AtomicReference<LlmSettings> settings = new AtomicReference<>();
    private final AtomicReference<Map<LlmProvider, String>> keys = new AtomicReference<>(Map.of());
    private final AtomicLong revision = new AtomicLong();

    /** v0.0.7 🍊 Injects collaborators; settings are loaded lazily on first use. */
    public SettingsService(AppSettingRepository store, SecretVault vault, Jsons jsons, SseHub hub, NaturalTime time) {
        this.store = store;
        this.vault = vault;
        this.jsons = jsons;
        this.hub = hub;
        this.time = time;
    }

    /** v0.0.7 🍊 Current settings (defaults when nothing was saved yet). */
    public LlmSettings current() {
        LlmSettings cached = settings.get();
        if (cached != null) {
            return cached;
        }
        LlmSettings loaded = store.get(SETTINGS_KEY).map(json -> jsons.read(json, LlmSettings.class))
                .orElseGet(LlmSettings::defaults);
        settings.compareAndSet(null, loaded);
        return settings.get();
    }

    /** v0.0.7 🍊 Decrypted API key of the current provider, if one was saved. */
    public Optional<String> apiKey() {
        LlmProvider provider = current().provider();
        String cached = keys.get().get(provider);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<String> loaded = store.get(KEY_PREFIX + provider.name())
                .map(json -> jsons.tree(json).path("cipher").asText())
                .map(cipher -> vault.decrypt(cipher, purpose(provider)));
        loaded.ifPresent(k -> keys.updateAndGet(m -> merged(m, provider, k)));
        return loaded;
    }

    /** v0.0.7 🍊 API key or NOT_CONFIGURED (used right before calling the provider). */
    public String requireApiKey() {
        return apiKey().orElseThrow(() -> new NotConfiguredException(
                "No API key is saved for " + current().provider() + ". Add one in the Console."));
    }

    /** v0.0.7 🍊 Saves provider, base URL and tiers after validation; publishes settings.changed. */
    public LlmSettingsView update(LlmProvider provider, String baseUrl, Map<ModelTier, TierSettings> tiers) {
        String url = baseUrl == null || baseUrl.isBlank() ? provider.defaultBaseUrl() : baseUrl.strip();
        validateBaseUrl(url);
        if (tiers != null) {
            tiers.forEach((tier, s) -> {
                if (s == null || s.model().isBlank()) {
                    throw new BadRequestException("Tier " + tier + " needs a model name.");
                }
            });
        }
        LlmSettings next = new LlmSettings(provider, url, tiers);
        store.put(SETTINGS_KEY, jsons.write(next), time.nowInstant());
        settings.set(next);
        revision.incrementAndGet();
        LlmSettingsView view = view();
        hub.publishAll(EventType.SETTINGS_CHANGED, null, view);
        return view;
    }

    /** v0.0.7 🍊 Encrypts and saves the API key of the current provider. */
    public LlmSettingsView saveApiKey(String apiKey) {
        String trimmed = apiKey == null ? "" : apiKey.strip();
        if (trimmed.length() < 8 || trimmed.length() > 500 || trimmed.contains(" ")) {
            throw new BadRequestException("That does not look like an API key.");
        }
        LlmProvider provider = current().provider();
        store.put(KEY_PREFIX + provider.name(),
                jsons.write(Map.of("cipher", vault.encrypt(trimmed, purpose(provider)))), time.nowInstant());
        keys.updateAndGet(m -> merged(m, provider, trimmed));
        revision.incrementAndGet();
        LlmSettingsView view = view();
        hub.publishAll(EventType.SETTINGS_CHANGED, null, view);
        return view;
    }

    /** v0.0.7 🍊 Public view with a masked key (contract type {@code LlmSettingsView}). */
    public LlmSettingsView view() {
        LlmSettings s = current();
        Optional<String> key = apiKey();
        return new LlmSettingsView(s.provider(), s.baseUrl(), key.isPresent(), key.map(SecretVault::mask).orElse(null),
                s.tiers());
    }

    /** v0.0.7 🍊 Model ids offered by the provider ({@code GET {baseUrl}/models}), sorted. */
    public List<String> listModels() {
        LlmSettings s = current();
        HttpRequest request = HttpRequest.newBuilder(URI.create(s.baseUrl() + "/models"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + requireApiKey())
                .GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new LlmAuthException("The provider rejected the API key (HTTP " + response.statusCode() + ").");
            }
            if (response.statusCode() >= 400) {
                throw new LlmTransportException("Listing models failed with HTTP " + response.statusCode() + ".",
                        response.statusCode(), response.statusCode() >= 500, 0, null);
            }
            List<String> ids = new ArrayList<>();
            for (JsonNode model : jsons.tree(response.body()).path("data")) {
                ids.add(model.path("id").asText());
            }
            ids.removeIf(String::isBlank);
            ids.sort(String::compareTo);
            return ids;
        } catch (IOException e) {
            throw new LlmTransportException("Could not reach " + s.baseUrl() + ": " + e.getMessage(), 0, true, 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmTransportException("Interrupted while listing models.", 0, false, 0, e);
        }
    }

    /** v0.0.7 🍊 Monotonic counter bumped on every settings or key change. */
    public long revision() {
        return revision.get();
    }

    /** v0.0.7 🍊 Only https (or local http) base URLs are accepted. */
    private static void validateBaseUrl(String url) {
        try {
            URI uri = URI.create(url);
            boolean local = "http".equals(uri.getScheme())
                    && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            if (!("https".equals(uri.getScheme()) || local) || uri.getHost() == null) {
                throw new BadRequestException("The base URL must use https (or http on localhost).");
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("The base URL is not a valid URL.");
        }
    }

    /** v0.0.7 🍊 Encryption purpose (AAD) binding a ciphertext to its provider. */
    private static String purpose(LlmProvider provider) {
        return "yuzu:llm-key:" + provider.name();
    }

    /** v0.0.7 🍊 Copy of the key map with one entry replaced. */
    private static Map<LlmProvider, String> merged(Map<LlmProvider, String> base, LlmProvider provider, String key) {
        EnumMap<LlmProvider, String> copy = new EnumMap<>(LlmProvider.class);
        copy.putAll(base);
        copy.put(provider, key);
        return Map.copyOf(copy);
    }
}
