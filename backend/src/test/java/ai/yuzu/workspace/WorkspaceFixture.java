package ai.yuzu.workspace;

import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.config.YuzuProperties;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/** v0.0.11 🍊 Builds a WorkspaceService over a temp folder with a fixed clock (Sat Sep 19, 11:32:05 AM PDT). */
final class WorkspaceFixture {

    /** v0.0.11 🍊 Workgroup zone used by the tests. */
    static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");

    /** v0.0.11 🍊 Fixed "now": 2026-09-19 11:32:05 in Los Angeles. */
    static final Instant NOW = Instant.parse("2026-09-19T18:32:05Z");

    /** v0.0.11 🍊 Static helpers only. */
    private WorkspaceFixture() {
    }

    /** v0.0.11 🍊 A service rooted at {@code base} with the given quota source. */
    static WorkspaceService service(Path base, WorkspaceQuota quota) {
        return new WorkspaceService(new YuzuProperties(ZONE, base.toString(), List.of(), null),
                new NaturalTime(Clock.fixed(NOW, ZoneOffset.UTC), ZONE), quota);
    }
}
