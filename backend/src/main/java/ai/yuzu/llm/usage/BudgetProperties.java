package ai.yuzu.llm.usage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * v0.0.31 🍊 Typed view of {@code yuzu.llm.budget.*}: the ceiling after which model calls pause.
 *
 * <p>Both ceilings are optional and off by default (a demo should not stop itself). Cost is derived from
 * the billable token count and one blended price, which is enough to stop runaway spending without a
 * per-model price table.</p>
 *
 * @param maxTotalTokens      billable tokens (prompt + completion) allowed in total; 0 means unlimited
 * @param maxCostUsd          spending ceiling in USD; null or non-positive means unlimited
 * @param usdPerMillionTokens blended price used to turn tokens into dollars
 */
@ConfigurationProperties(prefix = "yuzu.llm.budget")
public record BudgetProperties(long maxTotalTokens, BigDecimal maxCostUsd, BigDecimal usdPerMillionTokens) {

    /** v0.0.31 🍊 Normalizes the ceilings (negative or missing values mean "no limit"). */
    public BudgetProperties {
        maxTotalTokens = Math.max(0, maxTotalTokens);
        maxCostUsd = maxCostUsd == null || maxCostUsd.signum() <= 0 ? null : maxCostUsd;
        usdPerMillionTokens = usdPerMillionTokens == null || usdPerMillionTokens.signum() <= 0
                ? BigDecimal.ZERO : usdPerMillionTokens;
    }
}
