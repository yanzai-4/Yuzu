package ai.yuzu.settings;

import ai.yuzu.bootstrap.SnapshotBuilder;
import ai.yuzu.bootstrap.SnapshotContributor;
import org.springframework.stereotype.Component;

/** v0.0.7 🍊 Adds the (masked) model settings to the bootstrap snapshot. */
@Component
public class SettingsSnapshotContributor implements SnapshotContributor {

    private final SettingsService settings;

    /** v0.0.7 🍊 Injects the settings service. */
    public SettingsSnapshotContributor(SettingsService settings) {
        this.settings = settings;
    }

    /** v0.0.7 🍊 Contributes settings. */
    @Override
    public void contribute(String roomId, SnapshotBuilder snapshot) {
        snapshot.put("settings", settings.view());
    }
}
