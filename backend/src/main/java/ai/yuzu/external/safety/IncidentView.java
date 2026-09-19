package ai.yuzu.external.safety;

import java.util.List;

/** v0.0.16 🍊 A security incident as shown in the UI (contract type {@code Incident}). */
public record IncidentView(String id, String agentId, String stage, String verdict, List<String> reasons,
                           String excerpt, String time) {
}
