package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.NaturalTime;

import java.util.Map;

/** v0.0.12 🍊 Builds persisted-ready events: record id, natural clock, clipped text (1000 chars) and sanitized detail. */
final class ModuleEventFactory {

    /** v0.0.12 🍊 Maximum event text length (the module_event.text column). */
    static final int MAX_TEXT = 1_000;

    private final NaturalTime time;

    /** v0.0.12 🍊 Binds the clock used to stamp events. */
    ModuleEventFactory(NaturalTime time) {
        this.time = time;
    }

    /** v0.0.12 🍊 Creates one event; blank text becomes the phase default. */
    ModuleEvent create(AgentId agentId, ModuleKind module, EventPhase phase, String text, Map<String, ?> detail,
                       String traceId, String spanId, String parentSpanId) {
        String body = text == null || text.isBlank() ? phase.defaultText() : text.strip();
        return new ModuleEvent(IdGen.recordId(DataName.EVENT, agentId), agentId, module, phase,
                TextClip.truncate(body, MAX_TEXT), DetailSanitizer.sanitize(detail), traceId, spanId, parentSpanId,
                time.nowInstant());
    }
}
