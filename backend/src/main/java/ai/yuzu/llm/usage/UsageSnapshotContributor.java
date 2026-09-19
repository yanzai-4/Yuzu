package ai.yuzu.llm.usage;

import ai.yuzu.bootstrap.SnapshotBuilder;
import ai.yuzu.bootstrap.SnapshotContributor;
import org.springframework.stereotype.Component;

/** v0.0.31 🍊 Adds the usage metrics and the model budget state to the bootstrap snapshot. */
@Component
public class UsageSnapshotContributor implements SnapshotContributor {

    private final TokenMeter meter;
    private final TokenBudget budget;

    /** v0.0.31 🍊 Injects the meter and the budget. */
    public UsageSnapshotContributor(TokenMeter meter, TokenBudget budget) {
        this.meter = meter;
        this.budget = budget;
    }

    /** v0.0.31 🍊 Contributes usage and the budget (so a paused console shows why nothing is happening). */
    @Override
    public void contribute(String roomId, SnapshotBuilder snapshot) {
        snapshot.put("usage", meter.snapshot());
        snapshot.put("budget", budget.status());
    }
}
