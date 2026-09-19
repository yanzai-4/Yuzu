package ai.yuzu.monitor;

import java.util.Map;

/** v0.0.12 🍊 API shape of one monitor/trace event (contract type ModuleEvent); null fields are omitted from JSON. */
public record ModuleEventView(String id, String agentId, ModuleKind module, EventPhase phase, String text,
                              Map<String, Object> detail, String traceId, String spanId, String parentSpanId,
                              String time) {
}
