package ai.yuzu.bootstrap;

import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.realtime.SseHub;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * v0.0.5 🍊 Assembles the full room snapshot from every {@link SnapshotContributor}.
 *
 * <p>The event cursor is captured BEFORE the state is read, so anything published while the snapshot is
 * being built is replayed on the stream afterwards (the client upserts by id, so duplicates are harmless).</p>
 */
@Service
public class BootstrapService {

    private final List<SnapshotContributor> contributors;
    private final SseHub hub;
    private final NaturalTime time;

    /** v0.0.5 🍊 Injects every contributor. */
    public BootstrapService(List<SnapshotContributor> contributors, SseHub hub, NaturalTime time) {
        this.contributors = contributors;
        this.hub = hub;
        this.time = time;
    }

    /** v0.0.5 🍊 Builds the snapshot of a room. */
    public Map<String, Object> snapshot(String roomId) {
        long cursor = hub.currentCursor();
        SnapshotBuilder builder = new SnapshotBuilder(roomId);
        for (SnapshotContributor contributor : contributors) {
            contributor.contribute(roomId, builder);
        }
        return builder.put("eventCursor", cursor).put("time", time.compact(time.nowInstant())).build();
    }
}
