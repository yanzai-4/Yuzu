package ai.yuzu;

import ai.yuzu.common.time.NaturalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.1 🍊 Smoke test: the Spring context starts and core beans are wired. */
@SpringBootTest
class YuzuApplicationTests {

    @Autowired
    private NaturalTime naturalTime;

    /** v0.0.1 🍊 The context loads and the natural-time bean uses the workgroup zone. */
    @Test
    void contextLoads() {
        assertThat(naturalTime.zone().getId()).isEqualTo("America/Los_Angeles");
    }
}
