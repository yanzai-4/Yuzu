package ai.yuzu.llm.usage;

import ai.yuzu.common.error.ApiError;
import ai.yuzu.common.error.BudgetExhaustedException;
import ai.yuzu.common.error.ErrorCode;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * v0.0.31 🍊 Automatic pause when the configured token or cost budget is used up.
 *
 * <p>Reads the billable tokens {@link TokenMeter} already counts (nothing is metered twice) and is consulted
 * by {@code PriorityGate} before every model call. The first call that finds the budget exhausted flips the
 * pause under a {@link ReentrantLock}, publishes one realtime {@code error} event, and from then on every
 * call is refused with {@link BudgetExhaustedException} — so eight agents degrade gracefully instead of
 * hammering the provider. A human raises the ceiling (or resumes after it was raised elsewhere) to continue.</p>
 */
@Component
public class TokenBudget {

    private static final Logger log = LoggerFactory.getLogger(TokenBudget.class);
    private static final BigDecimal MILLION = new BigDecimal("1000000");

    /** v0.0.31 🍊 What the console shows: the ceilings, what was used, and whether calls are paused. */
    public record Status(boolean paused, long usedTokens, long maxTotalTokens, String usedUsd, String maxCostUsd,
                         String reason, String pausedAt) {
    }

    private final TokenMeter meter;
    private final SseHub hub;
    private final NaturalTime time;
    private final BigDecimal usdPerMillionTokens;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile long maxTotalTokens;
    private volatile BigDecimal maxCostUsd;
    private volatile boolean paused;
    private volatile String reason;
    private volatile String pausedAt;

    /** v0.0.31 🍊 Injects the meter, the configured ceilings and the realtime hub. */
    public TokenBudget(TokenMeter meter, BudgetProperties properties, SseHub hub, NaturalTime time) {
        this.meter = meter;
        this.hub = hub;
        this.time = time;
        this.maxTotalTokens = properties.maxTotalTokens();
        this.maxCostUsd = properties.maxCostUsd();
        this.usdPerMillionTokens = properties.usdPerMillionTokens();
    }

    /** v0.0.31 🍊 Throws when the budget is used up; called before every model call. */
    public void checkAvailable() {
        if (paused) {
            throw new BudgetExhaustedException(reason);
        }
        String breach = breach();
        if (breach == null) {
            return;
        }
        pause(breach);
        throw new BudgetExhaustedException(reason);
    }

    /** v0.0.31 🍊 True while new model calls are refused. */
    public boolean isPaused() {
        return paused;
    }

    /** v0.0.31 🍊 Current ceilings, consumption and pause state. */
    public Status status() {
        return new Status(paused, meter.billableTokens(), maxTotalTokens, money(usedUsd()),
                money(maxCostUsd), reason, pausedAt);
    }

    /** v0.0.31 🍊 Replaces the ceilings (0 / null = unlimited) and lifts the pause when there is headroom again. */
    public void setLimits(long tokens, BigDecimal costUsd) {
        lock.lock();
        try {
            maxTotalTokens = Math.max(0, tokens);
            maxCostUsd = costUsd == null || costUsd.signum() <= 0 ? null : costUsd;
            if (paused && breach() == null) {
                clear();
            }
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.31 🍊 Lifts the pause when the budget allows it again; false when it is still exhausted. */
    public boolean resume() {
        lock.lock();
        try {
            if (!paused) {
                return true;
            }
            if (breach() != null) {
                return false;
            }
            clear();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** v0.0.31 🍊 Which ceiling is exceeded, or null while there is headroom. */
    private String breach() {
        long used = meter.billableTokens();
        long tokenLimit = maxTotalTokens;
        if (tokenLimit > 0 && used >= tokenLimit) {
            return "The token budget is used up (" + used + " of " + tokenLimit + " tokens).";
        }
        BigDecimal costLimit = maxCostUsd;
        if (costLimit != null && usedUsd().compareTo(costLimit) >= 0) {
            return "The cost budget is used up ($" + money(usedUsd()) + " of $" + money(costLimit) + ").";
        }
        return null;
    }

    /** v0.0.31 🍊 Flips the pause and announces it once, however many agents hit the ceiling together. */
    private void pause(String why) {
        lock.lock();
        try {
            if (paused) {
                return;
            }
            reason = why;
            pausedAt = time.now();
            paused = true;
        } finally {
            lock.unlock();
        }
        log.warn("🍊 Model calls paused: {}", why);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("usedTokens", meter.billableTokens());
        details.put("maxTotalTokens", maxTotalTokens);
        details.put("usedUsd", money(usedUsd()));
        details.put("maxCostUsd", money(maxCostUsd));
        hub.publishAll(EventType.ERROR, null,
                new ApiError(ErrorCode.BUDGET_EXHAUSTED.name(), why, details, null, pausedAt));
    }

    /** v0.0.31 🍊 Clears the pause (always called with the lock held). */
    private void clear() {
        paused = false;
        reason = null;
        pausedAt = null;
        log.info("🍊 Model calls resumed; the budget has headroom again.");
    }

    /** v0.0.31 🍊 Money spent so far, from the billable tokens and the blended price. */
    private BigDecimal usedUsd() {
        if (usdPerMillionTokens.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(meter.billableTokens()).multiply(usdPerMillionTokens)
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    /** v0.0.31 🍊 Two-decimal string, or null when there is no ceiling. */
    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
