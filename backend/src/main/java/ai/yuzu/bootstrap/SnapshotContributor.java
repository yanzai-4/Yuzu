package ai.yuzu.bootstrap;

/** v0.0.5 🍊 A feature package's contribution to the room snapshot returned by {@code /api/bootstrap}. */
public interface SnapshotContributor {

    /** v0.0.5 🍊 Adds this feature's state for the room to the builder. */
    void contribute(String roomId, SnapshotBuilder snapshot);
}
