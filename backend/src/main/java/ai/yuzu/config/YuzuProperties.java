package ai.yuzu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

/**
 * v0.0.1 🍊 Typed view of the {@code yuzu.*} configuration namespace.
 *
 * @param zone          workgroup time zone for every natural-language time
 * @param workspaceRoot root directory of the per-agent workspaces
 * @param corsOrigins   origins allowed to call the API directly
 */
@ConfigurationProperties(prefix = "yuzu")
public record YuzuProperties(ZoneId zone, Path workspaceRoot, List<String> corsOrigins) {

    /** v0.0.1 🍊 Applies safe defaults for missing values. */
    public YuzuProperties {
        zone = zone == null ? ZoneId.of("America/Los_Angeles") : zone;
        workspaceRoot = (workspaceRoot == null ? Path.of("../workspaces") : workspaceRoot).toAbsolutePath().normalize();
        corsOrigins = corsOrigins == null ? List.of() : List.copyOf(corsOrigins);
    }
}
