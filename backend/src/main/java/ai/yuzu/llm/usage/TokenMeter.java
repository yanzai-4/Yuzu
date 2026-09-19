package ai.yuzu.llm.usage;

import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/**
 * v0.0.11 🍊 Lock-free token and cache-hit accounting by agent, module, tier and model.
 *
 * <p>Counters are {@link LongAdder}s (no contention between agents). The hit rate is Σcached ÷ Σprompt over
 * calls whose provider reported cache usage. A {@code usage.tick} snapshot is pushed at most every 2 s and
 * only when something changed. Local response-cache hits are counted separately.</p>
 */
@Component
public class TokenMeter {

    /** v0.0.11 🍊 Dimension key of one counter cell. */
    public record Key(String agentId, String module, String tier, String model) {
    }

    private final Map<Key, Cell> cells = new ConcurrentHashMap<>();
    private final LongAdder billable = new LongAdder();
    private final LongAdder localHits = new LongAdder();
    private final LongAdder localLookups = new LongAdder();
    private final LongAdder version = new LongAdder();
    private final SseHub hub;
    private final NaturalTime time;
    private final ScheduledFuture<?> ticker;
    private volatile long publishedVersion = -1;

    /** v0.0.11 🍊 Schedules the throttled usage.tick publisher. */
    public TokenMeter(SseHub hub, NaturalTime time, ScheduledExecutorService timerExecutor) {
        this.hub = hub;
        this.time = time;
        this.ticker = timerExecutor.scheduleAtFixedRate(this::publishIfChanged, 2, 2, TimeUnit.SECONDS);
    }

    /** v0.0.11 🍊 Counts one logical call (before its HTTP attempts). */
    public void recordCall(Key key) {
        cell(key).calls.increment();
        version.increment();
    }

    /** v0.0.31 🍊 Counts one HTTP attempt with its usage (usage may be {@link Usage#NONE} on failure). */
    public void recordAttempt(Key key, Usage usage, long latencyMs, boolean error) {
        Cell c = cell(key);
        c.attempts.increment();
        billable.add((long) usage.promptTokens() + usage.completionTokens());
        c.prompt.add(usage.promptTokens());
        c.cached.add(usage.cachedTokens());
        c.cacheWrite.add(usage.cacheWriteTokens());
        c.completion.add(usage.completionTokens());
        c.reasoning.add(usage.reasoningTokens());
        c.latency.add(latencyMs);
        if (usage.cacheReported()) {
            c.cacheReportedPrompt.add(usage.promptTokens());
        }
        if (error) {
            c.errors.increment();
        }
        version.increment();
    }

    /** v0.0.11 🍊 Counts a format or transport retry. */
    public void recordRetry(Key key) {
        cell(key).retries.increment();
        version.increment();
    }

    /** v0.0.11 🍊 Counts a local response-cache lookup (hit or miss). */
    public void recordLocalCache(boolean hit) {
        localLookups.increment();
        if (hit) {
            localHits.increment();
        }
        version.increment();
    }

    /** v0.0.31 🍊 Billable tokens (prompt + completion) across every agent, module, tier and model. */
    public long billableTokens() {
        return billable.sum();
    }

    /** v0.0.11 🍊 Aggregated snapshot (contract type {@code UsageSnapshot}). */
    public UsageSnapshot snapshot() {
        return new UsageSnapshot(aggregate(k -> "total").getFirst(), aggregate(Key::agentId),
                aggregate(Key::module), aggregate(Key::tier), aggregate(Key::model), localHits.sum(),
                localLookups.sum(), time.compact(time.nowInstant()));
    }

    /** v0.0.11 🍊 Stops the ticker. */
    @PreDestroy
    public void stop() {
        ticker.cancel(false);
    }

    /** v0.0.11 🍊 Publishes a snapshot when counters changed since the last tick. */
    private void publishIfChanged() {
        long current = version.sum();
        if (current != publishedVersion) {
            publishedVersion = current;
            hub.publishAll(EventType.USAGE_TICK, null, snapshot());
        }
    }

    /** v0.0.11 🍊 Groups cells by a dimension and sums them (sorted by prompt tokens, descending). */
    private List<UsageRow> aggregate(Function<Key, String> dimension) {
        Map<String, long[]> sums = new ConcurrentHashMap<>();
        cells.forEach((key, cell) -> {
            long[] s = sums.computeIfAbsent(dimension.apply(key), k -> new long[10]);
            s[0] += cell.calls.sum();
            s[1] += cell.attempts.sum();
            s[2] += cell.prompt.sum();
            s[3] += cell.cached.sum();
            s[4] += cell.completion.sum();
            s[5] += cell.reasoning.sum();
            s[6] += cell.errors.sum();
            s[7] += cell.retries.sum();
            s[8] += cell.cacheReportedPrompt.sum();
        });
        if (sums.isEmpty()) {
            sums.put(dimension.apply(new Key("total", "total", "total", "total")), new long[10]);
        }
        List<UsageRow> rows = new ArrayList<>();
        sums.forEach((name, s) -> rows.add(new UsageRow(name, s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7],
                s[8] == 0 ? null : (double) s[3] / s[8])));
        rows.sort(Comparator.comparingLong(UsageRow::promptTokens).reversed().thenComparing(UsageRow::key));
        return rows;
    }

    /** v0.0.11 🍊 Counter cell for one key (created on first use). */
    private Cell cell(Key key) {
        return cells.computeIfAbsent(key, k -> new Cell());
    }

    /** v0.0.11 🍊 LongAdder counters of one key. */
    private static final class Cell {
        private final LongAdder calls = new LongAdder();
        private final LongAdder attempts = new LongAdder();
        private final LongAdder prompt = new LongAdder();
        private final LongAdder cached = new LongAdder();
        private final LongAdder cacheWrite = new LongAdder();
        private final LongAdder completion = new LongAdder();
        private final LongAdder reasoning = new LongAdder();
        private final LongAdder latency = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder retries = new LongAdder();
        private final LongAdder cacheReportedPrompt = new LongAdder();
    }
}
