package ai.yuzu.monitor;

import java.time.Duration;

/** v0.0.12 🍊 Status-board limits: status/summary/refresh intervals, ERROR hold, recent events and max running spans per agent. */
record MonitorTimings(Duration statusInterval, Duration summaryInterval, Duration errorHold, Duration refreshInterval,
                      int recentEvents, int maxSpans) {

    /** v0.0.12 🍊 Production values: at most 4 statuses/s, 1 summary per 4 s, ERROR sticky for 5 s, refresh every 15 s. */
    static final MonitorTimings DEFAULTS = new MonitorTimings(Duration.ofMillis(250), Duration.ofSeconds(4),
            Duration.ofSeconds(5), Duration.ofSeconds(15), 20, 256);
}
