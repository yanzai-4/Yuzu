package ai.yuzu.llm.usage;

import ai.yuzu.bootstrap.SnapshotBuilder;
import ai.yuzu.bootstrap.SnapshotContributor;
import org.springframework.stereotype.Component;

/** v0.0.11 🍊 Adds the usage metrics to the bootstrap snapshot. */
@Component
public class UsageSnapshotContributor implements SnapshotContributor {

    private final TokenMeter meter;

    /** v0.0.11 🍊 Injects the meter. */
    public UsageSnapshotContributor(TokenMeter meter) {
        this.meter = meter;
    }

    /** v0.0.11 🍊 Contributes usage. */
    @Override
    public void contribute(String roomId, SnapshotBuilder snapshot) {
        snapshot.put("usage", meter.snapshot());
    }
}
