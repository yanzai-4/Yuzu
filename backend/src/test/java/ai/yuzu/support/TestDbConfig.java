package ai.yuzu.support;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** v0.0.2 🍊 Test-only configuration: start every test context from a clean, freshly migrated schema. */
@TestConfiguration
public class TestDbConfig {

    /** v0.0.2 🍊 Cleans then migrates the yuzu_test schema. */
    @Bean
    public FlywayMigrationStrategy cleanMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
