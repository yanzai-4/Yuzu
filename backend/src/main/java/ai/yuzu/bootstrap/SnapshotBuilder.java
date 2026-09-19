package ai.yuzu.bootstrap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.0.5 🍊 Mutable accumulator that feature packages fill through {@link SnapshotContributor}s.
 *
 * <p>Fields are typed loosely on purpose: the bootstrap package must not depend on feature packages;
 * each feature contributes its own DTOs (which match the contract in docs/API.md).</p>
 */
public final class SnapshotBuilder {

    private final Map<String, Object> fields = new LinkedHashMap<>();

    /** v0.0.5 🍊 Creates a builder with contract defaults (empty lists, zero usage). */
    public SnapshotBuilder(String roomId) {
        fields.put("roomId", roomId);
        fields.put("roomName", roomId);
        for (String list : List.of("users", "agents", "statuses", "messages", "cards", "tickets", "taskLists",
                "emails", "trades", "portfolios", "incidents")) {
            fields.put(list, new ArrayList<>());
        }
        fields.put("usage", null);
        fields.put("settings", null);
        fields.put("eventCursor", 0L);
        fields.put("time", "");
    }

    /** v0.0.5 🍊 Sets a scalar or object field. */
    public SnapshotBuilder put(String field, Object value) {
        fields.put(field, value);
        return this;
    }

    /** v0.0.5 🍊 Appends all values to a list field. */
    @SuppressWarnings("unchecked")
    public SnapshotBuilder addAll(String listField, List<?> values) {
        ((List<Object>) fields.computeIfAbsent(listField, k -> new ArrayList<>())).addAll(values);
        return this;
    }

    /** v0.0.5 🍊 The accumulated snapshot, serialized as the contract type {@code Snapshot}. */
    public Map<String, Object> build() {
        return fields;
    }
}
