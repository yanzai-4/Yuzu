package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** v0.0.12 🍊 One immutable monitor/trace event reported by a module (domain form; toView gives the contract shape). */
public record ModuleEvent(String id, AgentId agentId, ModuleKind module, EventPhase phase, String text,
                          Map<String, Object> detail, String traceId, String spanId, String parentSpanId,
                          Instant createdAt) {

    /** v0.0.12 🍊 Normalizes a null text and freezes the detail map (an empty map becomes null). */
    public ModuleEvent {
        text = text == null ? "" : text;
        detail = detail == null || detail.isEmpty() ? null : Collections.unmodifiableMap(new LinkedHashMap<>(detail));
    }

    /** v0.0.12 🍊 API view with a natural-language time. */
    public ModuleEventView toView(NaturalTime time) {
        return new ModuleEventView(id, agentId.value(), module, phase, text, detail, traceId, spanId, parentSpanId,
                time.compact(createdAt));
    }
}
