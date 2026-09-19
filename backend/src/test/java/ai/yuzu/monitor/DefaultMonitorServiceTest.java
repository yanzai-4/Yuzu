package ai.yuzu.monitor;

import ai.yuzu.common.error.ToolExecutionException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.realtime.EventType;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** v0.0.12 🍊 MonitorService end to end without MySQL: spans, traces, clipping, isolation of failures and concurrency. */
class DefaultMonitorServiceTest {

    private static final MonitorTimings FAST = new MonitorTimings(Duration.ofMillis(100), Duration.ofMillis(300),
            Duration.ofMillis(300), Duration.ofHours(1), 20, 256);

    private final List<ModuleEvent> persisted = new CopyOnWriteArrayList<>();
    private BoardFixture fixture;
    private DefaultMonitorService monitor;

    /** v0.0.12 🍊 Wires the service over the in-memory fixture. */
    @BeforeEach
    void setUp() {
        fixture = new BoardFixture(new CodeBubbleSummarizer(), FAST);
        monitor = new DefaultMonitorService(persisted::add, fixture.board, fixture.hub, fixture.time);
    }

    /** v0.0.12 🍊 Stops the fixture. */
    @AfterEach
    void tearDown() {
        fixture.close();
    }

    /** v0.0.12 🍊 START/STATE/END are persisted, applied to the board and published as module.event, in order. */
    @Test
    void spanLifecycleIsPersistedAppliedAndPublished() throws Exception {
        AgentId agent = fixture.agent();
        Span span = monitor.start(agent, ModuleKind.MAIN, "Deciding what to do next", null, null);
        assertThat(span.traceId()).matches("^trace-" + agent.hex() + "-[0-9a-f]{10}$");
        assertThat(span.spanId()).matches("^span-" + agent.hex() + "-[0-9a-f]{10}$");
        assertThat(fixture.board.status(agent).state()).isEqualTo("THINKING");

        span.state("Planning the product launch");
        span.detail("inputs", List.of("pool-1", "pool-2"));
        span.end("Decided to ACT");
        span.close();
        span.fail("too late");

        assertThat(persisted).extracting(ModuleEvent::phase)
                .containsExactly(EventPhase.START, EventPhase.STATE, EventPhase.END);
        assertThat(persisted).extracting(ModuleEvent::spanId).containsOnly(span.spanId());
        assertThat(persisted).extracting(ModuleEvent::traceId).containsOnly(span.traceId());
        ModuleEvent end = persisted.get(2);
        assertThat(end.id()).matches("^event-" + agent.hex() + "-[0-9a-f]{10}$");
        assertThat(end.detail()).containsKey("durationMs").containsEntry("inputs", List.of("pool-1", "pool-2"));
        assertThat(fixture.board.status(agent).state()).isEqualTo("IDLE");

        fixture.recorder.await("three module events",
                () -> fixture.recorder.data(EventType.MODULE_EVENT, agent.value()).size() == 3);
        List<JsonNode> published = fixture.recorder.data(EventType.MODULE_EVENT, agent.value());
        assertThat(published).extracting(node -> node.get("phase").asText()).containsExactly("START", "STATE", "END");
        assertThat(published.get(0).get("module").asText()).isEqualTo("MAIN");
        assertThat(published.get(2).get("text").asText()).isEqualTo("Decided to ACT");
        assertThat(published.get(2).get("spanId").asText()).isEqualTo(span.spanId());
    }

    /** v0.0.12 🍊 Child spans and INFO events join the parent trace and point at their parent span. */
    @Test
    void childSpansAndInfoEventsJoinTheParentTrace() {
        AgentId agent = fixture.agent();
        Span parent = monitor.start(agent, ModuleKind.TOOL_CALLING, "Turning the plan into tool calls",
                "trace-0000-00000000aa", null);
        assertThat(parent.traceId()).isEqualTo("trace-0000-00000000aa");
        Span child = parent.child(ModuleKind.TOOL, "Searching the web");
        monitor.info(agent, ModuleKind.TOOL, "Skipped a paywalled page", Map.of("url", "https://example.test"),
                parent.traceId(), child.spanId());
        child.fail(new ToolExecutionException("The search provider timed out."));
        parent.end("Dispatched 1 call");

        ModuleEvent childStart = persisted.get(1);
        assertThat(childStart.module()).isEqualTo(ModuleKind.TOOL);
        assertThat(childStart.traceId()).isEqualTo(parent.traceId());
        assertThat(childStart.parentSpanId()).isEqualTo(parent.spanId());
        ModuleEvent info = persisted.get(2);
        assertThat(info.phase()).isEqualTo(EventPhase.INFO);
        assertThat(info.spanId()).isNull();
        assertThat(info.parentSpanId()).isEqualTo(child.spanId());
        assertThat(info.detail()).containsEntry("url", "https://example.test");
        ModuleEvent failure = persisted.get(3);
        assertThat(failure.phase()).isEqualTo(EventPhase.ERROR);
        assertThat(failure.text()).isEqualTo("Failed: The search provider timed out.");
        assertThat(failure.detail()).containsEntry("errorCode", "TOOL_EXECUTION")
                .containsEntry("errorType", "ToolExecutionException");
        assertThat(fixture.board.status(agent).state()).isEqualTo("ERROR");
    }

    /** v0.0.12 🍊 Ids are made column-safe, texts are clipped to 1000 characters and blank texts get defaults. */
    @Test
    void idsAreSanitizedAndTextsClipped() {
        AgentId agent = fixture.agent();
        Span span = monitor.start(agent, ModuleKind.TOOL, "x".repeat(5_000),
                "a trace id with spaces that is far too long for the column", "  ");
        ModuleEvent start = persisted.get(0);
        assertThat(start.text()).hasSize(ModuleEventFactory.MAX_TEXT).endsWith("…");
        assertThat(TraceIds.isValid(start.traceId())).isTrue();
        assertThat(start.parentSpanId()).isNull();
        monitor.info(agent, ModuleKind.SYSTEM, "   ", null);
        assertThat(persisted.get(1).text()).isEqualTo("Noted");
        span.end(null);
        assertThat(persisted.get(2).text()).isEqualTo("done");
    }

    /** v0.0.12 🍊 Nothing a module does with the API can throw, and whatever is persisted still serializes to JSON. */
    @Test
    void reportingNeverThrows() {
        AgentId agent = fixture.agent();
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("self", cyclic);
        Object hostile = new Object() {
            /** v0.0.12 🍊 A toString that always fails. */
            @Override
            public String toString() {
                throw new IllegalStateException("boom");
            }
        };
        assertThatCode(() -> {
            Span orphan = monitor.start(null, ModuleKind.MAIN, "no agent", null, null);
            orphan.state("still nothing");
            orphan.end("done");
            monitor.start(agent, null, "no module", null, null).close();
            monitor.info(null, ModuleKind.SYSTEM, "no agent", null);
            monitor.info(agent, ModuleKind.SYSTEM, "weird detail",
                    Map.of("nan", Double.NaN, "cyclic", cyclic, "hostile", hostile));
            monitor.setPoolSize(null, 3);
            monitor.setPendingBatches(null, 1);
            monitor.setWaiting(null, true, null);
            monitor.setPaused(null, true);
            Span span = monitor.start(agent, ModuleKind.TOOL, "detail torture", null, null);
            span.detail(null, "ignored").detail("hostile", hostile).detail("cyclic", cyclic)
                    .fail(new IllegalStateException("secret-key-123"));
        }).doesNotThrowAnyException();

        for (ModuleEvent event : persisted) {
            assertThatCode(() -> fixture.mapper.writeValueAsString(event.detail())).doesNotThrowAnyException();
        }
        ModuleEvent failure = persisted.get(persisted.size() - 1);
        assertThat(failure.phase()).isEqualTo(EventPhase.ERROR);
        assertThat(failure.text()).isEqualTo("Failed unexpectedly (IllegalStateException)").doesNotContain("secret");
    }

    /** v0.0.12 🍊 A failing persistence step still lets the board and the stream see the event. */
    @Test
    void aFailingStepNeverSkipsTheOthers() throws Exception {
        DefaultMonitorService broken = new DefaultMonitorService(event -> {
            throw new IllegalStateException("database down");
        }, fixture.board, fixture.hub, fixture.time);
        AgentId agent = fixture.agent();
        Span span = broken.start(agent, ModuleKind.TOOL, "Uploading the file", null, null);
        assertThat(fixture.board.status(agent).state()).isEqualTo("WORKING");
        fixture.recorder.await("published although persistence failed",
                () -> !fixture.recorder.data(EventType.MODULE_EVENT, agent.value()).isEmpty());
        span.end("Uploaded");
        assertThat(fixture.board.status(agent).state()).isEqualTo("IDLE");
    }

    /** v0.0.12 🍊 MONITOR spans are traced but invisible on the desk; a CHAT span shows TALKING while it posts. */
    @Test
    void quietAndTalkingSpans() {
        AgentId agent = fixture.agent();
        Span summary = monitor.start(agent, ModuleKind.MONITOR, "Summarizing the desk bubble", null, null);
        assertThat(fixture.board.status(agent).state()).isEqualTo("IDLE");
        assertThat(persisted).hasSize(1);
        summary.end("done");

        Span chat = monitor.start(agent, ModuleKind.CHAT, "Reading Bob's message", null, null);
        assertThat(fixture.board.status(agent).state()).isEqualTo("WORKING");
        chat.desk(DeskState.TALKING).state("Replying to Bob");
        AgentStatusView talking = fixture.board.status(agent);
        assertThat(talking.state()).isEqualTo("TALKING");
        assertThat(talking.bubble()).isEqualTo(new AgentStatusView.Bubble("Chat", "Replying to Bob"));
        chat.desk(DeskState.IDLE);
        assertThat(fixture.board.status(agent).state()).isEqualTo("IDLE");
        chat.end("Replied");
        assertThat(persisted).extracting(ModuleEvent::module).containsOnly(ModuleKind.MONITOR, ModuleKind.CHAT);
    }

    /** v0.0.12 🍊 Many virtual threads reporting at once lose nothing and leave the desk idle. */
    @Test
    void concurrentReportingFromManyThreads() {
        AgentId agent = fixture.agent();
        int threads = 32;
        int spansPerThread = 25;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threads; t++) {
                int thread = t;
                pool.submit(() -> {
                    for (int i = 0; i < spansPerThread; i++) {
                        try (Span span = monitor.start(agent, ModuleKind.SUBCONSCIOUS,
                                "Thread " + thread + " thought " + i, null, null)) {
                            span.state("still thinking");
                        }
                    }
                });
            }
        }
        assertThat(persisted).hasSize(threads * spansPerThread * 3);
        assertThat(fixture.board.status(agent).state()).isEqualTo("IDLE");
        assertThat(fixture.asyncFailures).isEmpty();
    }

    /** v0.0.12 🍊 The no-op monitor reports nothing but keeps trace propagation working (for other modules' unit tests). */
    @Test
    void noopMonitorKeepsTracePropagation() {
        MonitorService noop = MonitorService.noop();
        Span span = noop.start(AgentId.of("agent-1a2b"), ModuleKind.MAIN, "Thinking", "trace-1a2b-0000000001", null);
        assertThat(span.traceId()).isEqualTo("trace-1a2b-0000000001");
        assertThat(span.child(ModuleKind.TOOL, "Searching").traceId()).isEqualTo(span.traceId());
        assertThat(noop.start(AgentId.of("agent-1a2b"), ModuleKind.MAIN, "Root").traceId()).startsWith("trace-1a2b-");
        span.end("done");
        assertThat(span.finished()).isTrue();
    }
}
