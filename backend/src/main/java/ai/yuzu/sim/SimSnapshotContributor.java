package ai.yuzu.sim;

import ai.yuzu.bootstrap.SnapshotBuilder;
import ai.yuzu.bootstrap.SnapshotContributor;
import org.springframework.stereotype.Component;

/** v0.0.11 🍊 Adds the room's simulated e-mails, trades and portfolios to the {@code /api/bootstrap} snapshot. */
@Component
public class SimSnapshotContributor implements SnapshotContributor {

    /** v0.0.11 🍊 E-mails and trades included in a snapshot (newest first); older ones come from /api/sim. */
    static final int BOOTSTRAP_LIMIT = 50;

    private final SimFeed feed;

    /** v0.0.11 🍊 Injects the read-side feed. */
    public SimSnapshotContributor(SimFeed feed) {
        this.feed = feed;
    }

    /** v0.0.11 🍊 Contributes emails, trades and portfolios of the room's present agents. */
    @Override
    public void contribute(String roomId, SnapshotBuilder snapshot) {
        snapshot.addAll("emails", feed.emails(roomId, null, BOOTSTRAP_LIMIT));
        snapshot.addAll("trades", feed.trades(roomId, null, BOOTSTRAP_LIMIT));
        snapshot.addAll("portfolios", feed.portfolios(roomId, null));
    }
}
