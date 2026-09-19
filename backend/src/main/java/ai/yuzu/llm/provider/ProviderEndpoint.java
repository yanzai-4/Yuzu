package ai.yuzu.llm.provider;

import java.net.URI;

/**
 * v0.0.8 🍊 Where and how to call the provider.
 *
 * @param baseUrl base URL without trailing slash (for example https://api.openai.com/v1)
 * @param apiKey  bearer token (never logged)
 */
public record ProviderEndpoint(String baseUrl, String apiKey) {

    /** v0.0.8 🍊 Host name of the base URL (capability rules are keyed by host). */
    public String host() {
        String host = URI.create(baseUrl).getHost();
        return host == null ? "" : host;
    }

    /** v0.0.8 🍊 Never prints the key. */
    @Override
    public String toString() {
        return "ProviderEndpoint[" + baseUrl + "]";
    }
}
