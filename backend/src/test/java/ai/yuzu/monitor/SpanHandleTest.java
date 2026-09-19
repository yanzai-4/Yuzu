package ai.yuzu.monitor;

import ai.yuzu.common.error.CancelledException;
import ai.yuzu.common.error.ToolExecutionException;
import ai.yuzu.common.id.AgentId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** v0.0.12 🍊 Span lifecycle: START/STATE/terminal events, idempotent end and close, failures, desk changes, children. */
class SpanHandleTest {

    private static final AgentId AGENT = AgentId.of("agent-1a2b");

    private final RecordingEmitter emitter = new RecordingEmitter();

    /** v0.0.12 🍊 START, STATE and END are reported in order; END carries the details and durationMs. */
    @Test
    void startStateEnd() {
        SpanHandle span = span(ModuleKind.CHAT);
        span.state("Deciding whether to reply").detail("messageId", "msg-0000-0123456789").end("Replied");
        assertThat(emitter.phases()).containsExactly(EventPhase.START, EventPhase.STATE, EventPhase.END);
        assertThat(emitter.events.get(0).text()).isEqualTo("Reading Alice's message");
        assertThat(emitter.events.get(1).detail()).isNull();
        assertThat(emitter.events.get(2).detail()).containsEntry("messageId", "msg-0000-0123456789")
                .containsKey("durationMs");
        assertThat(span.finished()).isTrue();
    }

    /** v0.0.12 🍊 Only the first terminal call counts; later calls, states, details and desk changes are ignored. */
    @Test
    void terminalCallsAreIdempotent() {
        SpanHandle span = span(ModuleKind.TOOL);
        span.end("ok");
        span.end("again");
        span.fail("late failure");
        span.fail(new IllegalStateException("late"));
        span.cancelled("late cancel");
        span.close();
        span.state("after the end");
        span.detail("late", 1);
        span.desk(DeskState.TALKING);
        assertThat(emitter.phases()).containsExactly(EventPhase.START, EventPhase.END);
        assertThat(emitter.deskChanges).isEmpty();
    }

    /** v0.0.12 🍊 Closing without end() reports END "done" (try-with-resources). */
    @Test
    void closeWithoutEndReportsDone() {
        try (Span span = span(ModuleKind.MAIN)) {
            span.state("Thinking");
        }
        assertThat(emitter.phases()).containsExactly(EventPhase.START, EventPhase.STATE, EventPhase.END);
        assertThat(emitter.events.get(2).text()).isEqualTo("done");
    }

    /** v0.0.12 🍊 Expected failures keep their message, unexpected ones only their type; cancellations are CANCELLED. */
    @Test
    void failuresAndCancellations() {
        span(ModuleKind.TOOL).fail(new ToolExecutionException("The mail server refused the message."));
        Recorded expected = emitter.last();
        assertThat(expected.phase()).isEqualTo(EventPhase.ERROR);
        assertThat(expected.text()).isEqualTo("Failed: The mail server refused the message.");
        assertThat(expected.detail()).containsEntry("errorCode", "TOOL_EXECUTION")
                .containsEntry("errorType", "ToolExecutionException");

        span(ModuleKind.TOOL).fail(new IllegalStateException("api key sk-secret leaked"));
        assertThat(emitter.last().text()).isEqualTo("Failed unexpectedly (IllegalStateException)");

        span(ModuleKind.TOOL).fail(new CancelledException("paused"));
        assertThat(emitter.last().phase()).isEqualTo(EventPhase.CANCELLED);
        assertThat(emitter.last().text()).isEqualTo("paused");

        span(ModuleKind.TOOL).fail("Quota exceeded");
        assertThat(emitter.last().phase()).isEqualTo(EventPhase.ERROR);
        assertThat(emitter.last().text()).isEqualTo("Quota exceeded");

        span(ModuleKind.TOOL).cancelled("interrupted by a human");
        assertThat(emitter.last().phase()).isEqualTo(EventPhase.CANCELLED);
    }

    /** v0.0.12 🍊 durationMs, errorType and errorCode always survive, however many details a module adds. */
    @Test
    void reservedDetailsAlwaysFit() {
        SpanHandle span = span(ModuleKind.TOOL);
        for (int i = 0; i < 40; i++) {
            span.detail("key" + i, i);
        }
        span.fail(new ToolExecutionException("Timed out."));
        Map<String, Object> detail = DetailSanitizer.sanitize(emitter.last().detail());
        assertThat(detail).containsKeys("durationMs", "errorType", "errorCode", "key0").doesNotContainKey("moreEntries");
        assertThat(detail).hasSize(DetailSanitizer.MAX_ENTRIES);
    }

    /** v0.0.12 🍊 A value whose toString throws an Error, or a failure whose message throws, never reaches the module. */
    @Test
    void hostileInputsNeverThrow() {
        SpanHandle span = span(ModuleKind.TOOL);
        Object hostile = new Object() {
            /** v0.0.12 🍊 Throws an Error instead of describing itself. */
            @Override
            public String toString() {
                throw new AssertionError("broken toString");
            }
        };
        ToolExecutionException broken = new ToolExecutionException("unused") {
            /** v0.0.12 🍊 Throws while being described. */
            @Override
            public String getMessage() {
                throw new IllegalStateException("broken message");
            }
        };
        assertThatCode(() -> {
            span.detail("hostile", hostile);
            span.fail(broken);
        }).doesNotThrowAnyException();
        assertThat(emitter.last().phase()).isEqualTo(EventPhase.ERROR);
        assertThat(span.finished()).isTrue();
    }

    /** v0.0.12 🍊 Desk changes are forwarded once per change; derived-only states are ignored. */
    @Test
    void deskChangesAreForwarded() {
        SpanHandle span = span(ModuleKind.CHAT);
        assertThat(span.currentDesk()).isEqualTo(DeskState.WORKING);
        span.desk(DeskState.TALKING).desk(DeskState.TALKING).desk(DeskState.PAUSED).desk(DeskState.ERROR).desk(null);
        assertThat(emitter.deskChanges).containsExactly(DeskState.TALKING);
        span.state("Posting the reply");
        assertThat(emitter.last().desk()).isEqualTo(DeskState.TALKING);
    }

    /** v0.0.12 🍊 A child span shares the trace and points at its parent. */
    @Test
    void childSpansShareTheTrace() {
        SpanHandle parent = span(ModuleKind.TOOL_CALLING);
        Span child = parent.child(ModuleKind.TOOL, "Searching the web");
        assertThat(child.traceId()).isEqualTo(parent.traceId());
        assertThat(((SpanHandle) child).parentSpanId()).isEqualTo(parent.spanId());
        assertThat(emitter.last().text()).isEqualTo("Searching the web");
    }

    /** v0.0.12 🍊 A broken emitter never breaks the reporting module. */
    @Test
    void emitterFailuresNeverReachTheCaller() {
        SpanEmitter broken = new SpanEmitter() {
            /** v0.0.12 🍊 Always fails. */
            @Override
            public void emit(SpanHandle span, EventPhase phase, String text, Map<String, Object> detail) {
                throw new IllegalStateException("emit");
            }

            /** v0.0.12 🍊 Always fails. */
            @Override
            public void deskChanged(SpanHandle span) {
                throw new IllegalStateException("desk");
            }

            /** v0.0.12 🍊 Always fails. */
            @Override
            public Span startChild(SpanHandle parent, ModuleKind module, String text) {
                throw new IllegalStateException("child");
            }
        };
        SpanHandle span = new SpanHandle(broken, AGENT, ModuleKind.MAIN, "trace-1a2b-0000000001",
                "span-1a2b-0000000001", null);
        assertThatCode(() -> {
            span.start("Thinking");
            span.state("Still thinking").desk(DeskState.TALKING).detail("object", new Object());
            assertThat(span.child(ModuleKind.TOOL, "child").traceId()).isEqualTo(span.traceId());
            span.end("done");
        }).doesNotThrowAnyException();
        assertThat(span.finished()).isTrue();
    }

    /** v0.0.12 🍊 A started span over the recording emitter. */
    private SpanHandle span(ModuleKind module) {
        SpanHandle span = new SpanHandle(emitter, AGENT, module, "trace-1a2b-0000000001",
                "span-1a2b-" + String.format("%010x", emitter.events.size()), null);
        span.start("Reading Alice's message");
        return span;
    }

    /** v0.0.12 🍊 One event seen by the recording emitter. */
    private record Recorded(EventPhase phase, String text, Map<String, Object> detail, DeskState desk) {
    }

    /** v0.0.12 🍊 Emitter recording events and desk changes; children use the same emitter. */
    private static final class RecordingEmitter implements SpanEmitter {

        private final List<Recorded> events = new CopyOnWriteArrayList<>();
        private final List<DeskState> deskChanges = new CopyOnWriteArrayList<>();

        /** v0.0.12 🍊 Records the event. */
        @Override
        public void emit(SpanHandle span, EventPhase phase, String text, Map<String, Object> detail) {
            events.add(new Recorded(phase, text, detail, span.currentDesk()));
        }

        /** v0.0.12 🍊 Records the new desk state. */
        @Override
        public void deskChanged(SpanHandle span) {
            deskChanges.add(span.currentDesk());
        }

        /** v0.0.12 🍊 Starts a child span in the parent's trace. */
        @Override
        public Span startChild(SpanHandle parent, ModuleKind module, String text) {
            SpanHandle child = new SpanHandle(this, parent.agentId(), module, parent.traceId(),
                    "span-1a2b-ffffffffff", parent.spanId());
            child.start(text);
            return child;
        }

        /** v0.0.12 🍊 Phases in order. */
        private List<EventPhase> phases() {
            return events.stream().map(Recorded::phase).toList();
        }

        /** v0.0.12 🍊 The latest event. */
        private Recorded last() {
            return events.get(events.size() - 1);
        }
    }
}
