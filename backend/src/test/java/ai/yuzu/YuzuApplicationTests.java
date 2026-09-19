package ai.yuzu;

import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.2 🍊 Smoke test: the Spring context (with database and migrations) starts and core beans are wired. */
@IntegrationTest
class YuzuApplicationTests {

    @Autowired
    private NaturalTime naturalTime;

    /** v0.0.2 🍊 The context loads and the natural-time bean uses the workgroup zone. */
    @Test
    void contextLoads() {
        assertThat(naturalTime.zone().getId()).isEqualTo("America/Los_Angeles");
    }
}
