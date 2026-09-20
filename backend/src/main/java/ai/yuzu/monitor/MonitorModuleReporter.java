package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.module.ModuleReporter;
import ai.yuzu.module.ModuleSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * v0.0.34 🍊 Connects every AI module and tool to the monitor: the adapter from {@link ModuleReporter} (what the
 * module layer knows) to {@link MonitorService} (desk states, bubbles, {@code module.event} SSE, {@code
 * module_event} rows, traces).
 *
 * <p>Without this bean the module layer falls back to logging only, so the office desks stay idle and the
 * trace stays empty however hard the agents work. Module names are the {@link ModuleKind} constants; an
 * unknown name is reported as {@link ModuleKind#SYSTEM} rather than dropped.</p>
 */
@Component
public class MonitorModuleReporter implements ModuleReporter {

    private static final Logger log = LoggerFactory.getLogger(MonitorModuleReporter.class);

    /** v0.0.34 🍊 Modules that report under a name of their own but belong to a kind the contract already has. */
    private static final Map<String, ModuleKind> ALIASES = Map.of(
            "MEMORY_READ", ModuleKind.MEMORY,
            "MEMORY_JUDGE", ModuleKind.MEMORY);

    private final MonitorService monitor;

    /** v0.0.34 🍊 Injects the monitor. */
    public MonitorModuleReporter(MonitorService monitor) {
        this.monitor = monitor;
    }

    /** v0.0.34 🍊 Starts a monitor span for a module of an agent (never throws). */
    @Override
    public ModuleSpan start(AgentId agentId, String module, String text, String traceId, String parentSpanId) {
        return new Adapter(monitor.start(agentId, kindOf(module), text, traceId, parentSpanId));
    }

    /** v0.0.34 🍊 Module name to {@link ModuleKind}; unknown names stay visible as SYSTEM. */
    static ModuleKind kindOf(String module) {
        if (module == null || module.isBlank()) {
            return ModuleKind.SYSTEM;
        }
        String name = module.strip().toUpperCase(Locale.ROOT);
        ModuleKind alias = ALIASES.get(name);
        if (alias != null) {
            return alias;
        }
        try {
            return ModuleKind.valueOf(name);
        } catch (IllegalArgumentException e) {
            log.debug("Unknown module name {} reported to the monitor", module);
            return ModuleKind.SYSTEM;
        }
    }

    /** v0.0.34 🍊 A monitor span seen through the module layer's narrower interface. */
    private record Adapter(Span span) implements ModuleSpan {

        /** v0.0.34 🍊 Progress update. */
        @Override
        public void state(String text) {
            span.state(text);
        }

        /** v0.0.34 🍊 Detail reported with the terminal event. */
        @Override
        public void detail(String key, Object value) {
            span.detail(key, value);
        }

        /** v0.0.34 🍊 Successful end. */
        @Override
        public void end(String text) {
            span.end(text);
        }

        /** v0.0.34 🍊 Failure (a cancellation becomes CANCELLED inside the span). */
        @Override
        public void fail(Throwable error) {
            span.fail(error);
        }

        /** v0.0.34 🍊 Cancelled by a pause, an interrupt or superseded work. */
        @Override
        public void cancelled(String reason) {
            span.cancelled(reason);
        }

        /** v0.0.34 🍊 Id children reference as their parent. */
        @Override
        public String spanId() {
            return span.spanId();
        }

        /** v0.0.34 🍊 Ends with "done" unless the span already finished. */
        @Override
        public void close() {
            span.close();
        }
    }
}
