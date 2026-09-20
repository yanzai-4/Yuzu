package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.module.ModuleSpan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.34 🍊 Every module name a module reports maps onto a monitor kind, and spans reach the monitor. */
class MonitorModuleReporterTest {

    /** v0.0.34 🍊 Known kinds pass through, memory sub-modules alias onto MEMORY, unknown names stay visible. */
    @Test
    void moduleNamesMapOntoKinds() {
        assertThat(MonitorModuleReporter.kindOf("CHAT")).isEqualTo(ModuleKind.CHAT);
        assertThat(MonitorModuleReporter.kindOf("wm_compactor")).isEqualTo(ModuleKind.WM_COMPACTOR);
        assertThat(MonitorModuleReporter.kindOf("MEMORY_READ")).isEqualTo(ModuleKind.MEMORY);
        assertThat(MonitorModuleReporter.kindOf("MEMORY_JUDGE")).isEqualTo(ModuleKind.MEMORY);
        assertThat(MonitorModuleReporter.kindOf("something-new")).isEqualTo(ModuleKind.SYSTEM);
        assertThat(MonitorModuleReporter.kindOf(null)).isEqualTo(ModuleKind.SYSTEM);
    }

    /** v0.0.34 🍊 A module span forwards start, state and the terminal event to the monitor service. */
    @Test
    void spansReachTheMonitor() {
        List<String> calls = new ArrayList<>();
        MonitorService monitor = new RecordingMonitor(calls);
        MonitorModuleReporter reporter = new MonitorModuleReporter(monitor);
        try (ModuleSpan span = reporter.start(AgentId.of("agent-1111"), "CHAT", "Reading a message", "trace-1", null)) {
            span.state("retry 2/3");
            span.detail("attempts", 2);
            span.end("Replying");
        }
        assertThat(calls).containsExactly("start:CHAT:Reading a message", "state:retry 2/3", "detail:attempts=2",
                "end:Replying", "close");
    }

    /** v0.0.34 🍊 Monitor service that records what a span reports. */
    private record RecordingMonitor(List<String> calls) implements MonitorService {

        /** v0.0.34 🍊 Records the start and returns a recording span. */
        @Override
        public Span start(AgentId agentId, ModuleKind module, String text, String traceId, String parentSpanId) {
            calls.add("start:" + module + ":" + text);
            return new RecordingSpan(calls);
        }

        /** v0.0.34 🍊 Unused by this test. */
        @Override
        public void info(AgentId agentId, ModuleKind module, String text, Map<String, ?> detail, String traceId,
                         String parentSpanId) {
        }

        /** v0.0.34 🍊 Unused by this test. */
        @Override
        public void setPoolSize(AgentId agentId, int size) {
        }

        /** v0.0.34 🍊 Unused by this test. */
        @Override
        public void setPendingBatches(AgentId agentId, int count) {
        }

        /** v0.0.34 🍊 Unused by this test. */
        @Override
        public void setWaiting(AgentId agentId, boolean waiting, String reason) {
        }

        /** v0.0.34 🍊 Unused by this test. */
        @Override
        public void setPaused(AgentId agentId, boolean paused) {
        }
    }

    /** v0.0.34 🍊 Span that records every call. */
    private record RecordingSpan(List<String> calls) implements Span {

        @Override
        public String traceId() {
            return "trace-1";
        }

        @Override
        public String spanId() {
            return "span-1";
        }

        @Override
        public Span state(String text) {
            calls.add("state:" + text);
            return this;
        }

        @Override
        public Span desk(DeskState desk) {
            return this;
        }

        @Override
        public Span detail(String key, Object value) {
            calls.add("detail:" + key + "=" + value);
            return this;
        }

        @Override
        public Span child(ModuleKind module, String text) {
            return this;
        }

        @Override
        public void end(String text) {
            calls.add("end:" + text);
        }

        @Override
        public void fail(Throwable error) {
            calls.add("fail:" + error.getMessage());
        }

        @Override
        public void fail(String reason) {
            calls.add("fail:" + reason);
        }

        @Override
        public void cancelled(String reason) {
            calls.add("cancelled:" + reason);
        }

        @Override
        public boolean finished() {
            return false;
        }

        @Override
        public void close() {
            calls.add("close");
        }
    }
}
