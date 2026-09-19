package ai.yuzu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

/**
 * v0.0.7 🍊 Typed view of the {@code yuzu.*} configuration namespace.
 *
 * @param zone          workgroup time zone for every natural-language time
 * @param workspaceRoot root directory of the per-agent workspaces (plain string; resolved against the
 *                      working directory, never as a classpath resource)
 * @param corsOrigins   origins allowed to call the API directly
 * @param secretDir     directory holding the master encryption key (never inside the repository)
 */
@ConfigurationProperties(prefix = "yuzu")
public record YuzuProperties(ZoneId zone, String workspaceRoot, List<String> corsOrigins, String secretDir) {

    /** v0.0.7 🍊 Applies safe defaults for missing values. */
    public YuzuProperties {
        zone = zone == null ? ZoneId.of("America/Los_Angeles") : zone;
        workspaceRoot = workspaceRoot == null || workspaceRoot.isBlank() ? "../workspaces" : workspaceRoot;
        corsOrigins = corsOrigins == null ? List.of() : List.copyOf(corsOrigins);
        secretDir = secretDir == null || secretDir.isBlank() ? "../data/secret" : secretDir;
    }

    /** v0.0.7 🍊 Absolute, normalized directory of the master key. */
    public Path secretDirPath() {
        return Path.of(secretDir).toAbsolutePath().normalize();
    }

    /** v0.0.2 🍊 Absolute, normalized workspace root directory. */
    public Path workspaceRootPath() {
        return Path.of(workspaceRoot).toAbsolutePath().normalize();
    }
}
