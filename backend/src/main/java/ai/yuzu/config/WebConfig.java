package ai.yuzu.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** v0.0.1 🍊 Web MVC settings: CORS for direct (non-proxied) frontend access. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final YuzuProperties properties;

    /** v0.0.1 🍊 Injects the typed configuration. */
    public WebConfig(YuzuProperties properties) {
        this.properties = properties;
    }

    /** v0.0.1 🍊 Allows the configured origins to call /api/**, including the SSE stream. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (properties.corsOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(properties.corsOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
