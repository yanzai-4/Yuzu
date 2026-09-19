package ai.yuzu.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * v0.0.2 🍊 Meta-annotation for tests that need the full context and the yuzu_test database.
 *
 * <p>Requires the local MySQL instance ({@code scripts/db.sh init-db}). The schema is cleaned and
 * re-migrated once per Spring test context.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestDbConfig.class)
public @interface IntegrationTest {
}
