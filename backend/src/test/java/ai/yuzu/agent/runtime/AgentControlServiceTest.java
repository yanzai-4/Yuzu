package ai.yuzu.agent.runtime;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.Limits;
import ai.yuzu.agent.Permission;
import ai.yuzu.agent.PermissionScope;
import ai.yuzu.agent.Role;
import ai.yuzu.common.concurrent.CancelToken;
import ai.yuzu.common.error.CancelledException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.json.Jsons;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.AttemptObserver;
import ai.yuzu.llm.LlmCall;
import ai.yuzu.llm.LlmExecutor;
import ai.yuzu.llm.capability.CapabilityRegistry;
import ai.yuzu.llm.provider.LlmMessage;
import ai.yuzu.llm.provider.OpenAiCompatibleProvider;
import ai.yuzu.llm.provider.ProviderEndpoint;
import ai.yuzu.llm.structured.OutputStrategy;
import ai.yuzu.monitor.AgentStatusBoard;
import ai.yuzu.monitor.AgentStatusView;
import ai.yuzu.monitor.ModuleKind;
import ai.yuzu.monitor.MonitorService;
import ai.yuzu.monitor.Span;
import ai.yuzu.settings.AppSettingRepository;
import ai.yuzu.settings.TierSettings;
import ai.yuzu.support.FakeLlmServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * v0.0.30 🍊 Agent controls: interrupt stops a streamed answer well inside one second, pause/resume persist the
 * state, and "stop all" cancels and pauses every coworker of a room. Cancellation always reports CANCELLED.
 */
class AgentControlServiceTest {

    private static final String ROOM = "room-0c0c";
    /** The bound the demo promises: a human presses Interrupt and the stream stops within one second. */
    private static final long INTERRUPT_BUDGET_MS = 1_000;

    private final ObjectMapper mapper = new ObjectMapper();
    private final NaturalTime time = new NaturalTime(Clock.systemUTC(), ZoneId.of("America/Los_Angeles"));

    private FakeLlmServer server;
    private LlmExecutor executor;
    private AgentService agents;
    private AgentRuntimeManager runtimes;
    private AgentStatusBoard statuses;
    private MonitorService monitor;
    private Span span;
    private AgentControlService control;

    /** v0.0.30 🍊 Wires the control service over mocked collaborators and a real streaming provider. */
    @BeforeEach
    void setUp() throws Exception {
        server = new FakeLlmServer();
        AppSettingRepository store = mock(AppSettingRepository.class);
        when(store.get(anyString())).thenReturn(Optional.empty());
        executor = new LlmExecutor(new OpenAiCompatibleProvider(mapper, Duration.ofSeconds(5)),
                new CapabilityRegistry(store, new Jsons(mapper), time));
        agents = mock(AgentService.class);
        runtimes = mock(AgentRuntimeManager.class);
        statuses = mock(AgentStatusBoard.class);
        monitor = mock(MonitorService.class);
        span = mock(Span.class);
        when(monitor.start(any(), any(), anyString(), any(), any())).thenReturn(span);
        when(statuses.status(any())).thenAnswer(call ->
                AgentStatusView.idle(call.getArgument(0, AgentId.class).value(), false, time.compact(Instant.now())));
        control = new AgentControlService(agents, runtimes, statuses, monitor, time);
    }

    /** v0.0.30 🍊 Stops the scripted provider. */
    @AfterEach
    void tearDown() {
        server.close();
    }

    /** v0.0.30 🍊 Interrupt aborts a never-ending streamed answer in well under a second and reports CANCELLED. */
    @Test
    void interruptStopsAStreamedAnswerWithinOneSecond() throws Exception {
        AgentRuntime runtime = runtime(AgentProfile.State.ACTIVE);
        CancelToken token = runtime.cancelToken();
        server.enqueueEndlessStream();

        CountDownLatch firstDelta = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicInteger deltas = new AtomicInteger();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicReference<Integer> deltasWhenCancelled = new AtomicReference<>();
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                executor.execute(streamCall(), OutputStrategy.PROMPT_ONLY, delta -> {
                    deltas.incrementAndGet();
                    firstDelta.countDown();
                }, token, AttemptObserver.NONE);
            } catch (Throwable t) {
                thrown.set(t);
            } finally {
                finished.countDown();
            }
        });

        assertThat(firstDelta.await(10, TimeUnit.SECONDS)).as("the provider started streaming").isTrue();
        long startedNanos = System.nanoTime();
        control.interrupt(runtime.agentId(), "a human pressed Interrupt");
        deltasWhenCancelled.set(deltas.get());

        assertThat(finished.await(INTERRUPT_BUDGET_MS, TimeUnit.MILLISECONDS))
                .as("the stream stops within %d ms of the interrupt", INTERRUPT_BUDGET_MS).isTrue();
        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000;
        worker.join();
        assertThat(elapsedMs).isLessThan(INTERRUPT_BUDGET_MS);
        assertThat(thrown.get()).isInstanceOf(CancelledException.class);
        assertThat(thrown.get()).hasMessageContaining("Interrupt");
        // Cooperative: the sink sees at most one more delta that was already in flight.
        assertThat(deltas.get() - deltasWhenCancelled.get()).isLessThanOrEqualTo(1);
        verify(monitor).start(eq(runtime.agentId()), eq(ModuleKind.SYSTEM), anyString(), any(), any());
        verify(span).cancelled(anyString());
    }

    /** v0.0.30 🍊 After an interrupt the agent keeps working: a fresh, uncancelled token is installed. */
    @Test
    void interruptInstallsAFreshTokenForTheNextTask() {
        AgentRuntime runtime = runtime(AgentProfile.State.ACTIVE);
        CancelToken before = runtime.cancelToken();

        control.interrupt(runtime.agentId(), "a human pressed Interrupt");

        assertThat(before.isCancelled()).isTrue();
        assertThat(runtime.cancelToken()).isNotSameAs(before);
        assertThat(runtime.cancelToken().isCancelled()).isFalse();
        verify(agents, never()).setState(any(), any());
    }

    /** v0.0.30 🍊 Pause persists PAUSED, cancels in-flight work and reports CANCELLED; resume persists ACTIVE. */
    @Test
    void pauseCancelsInFlightWorkAndResumeClearsIt() {
        AgentRuntime runtime = runtime(AgentProfile.State.ACTIVE);
        CancelToken running = runtime.cancelToken();

        control.pause(runtime.agentId());
        assertThat(running.isCancelled()).isTrue();
        verify(agents).setState(runtime.agentId(), AgentProfile.State.PAUSED);
        verify(span).cancelled(anyString());

        control.resume(runtime.agentId());
        verify(agents).setState(runtime.agentId(), AgentProfile.State.ACTIVE);
        assertThat(runtime.cancelToken().isCancelled()).isFalse();
    }

    /** v0.0.30 🍊 Stop all pauses and cancels every present coworker of the room and reports what it touched. */
    @Test
    void stopAllPausesAndCancelsEveryCoworkerOfTheRoom() {
        AgentRuntime first = runtime(AgentProfile.State.ACTIVE);
        AgentRuntime second = runtime(AgentProfile.State.ACTIVE);
        when(agents.list(ROOM)).thenReturn(List.of(first.profile(), second.profile()));
        CancelToken firstToken = first.cancelToken();
        CancelToken secondToken = second.cancelToken();

        RoomControlView result = control.stopAll(ROOM, "a human pressed Stop all");

        assertThat(result.roomId()).isEqualTo(ROOM);
        assertThat(result.action()).isEqualTo("STOP_ALL");
        assertThat(result.affected()).isEqualTo(2);
        assertThat(result.statuses()).hasSize(2);
        assertThat(firstToken.isCancelled()).isTrue();
        assertThat(secondToken.isCancelled()).isTrue();
        verify(agents).setState(first.agentId(), AgentProfile.State.PAUSED);
        verify(agents).setState(second.agentId(), AgentProfile.State.PAUSED);
    }

    /** v0.0.30 🍊 Resume all brings every paused coworker back. */
    @Test
    void resumeAllBringsEveryPausedCoworkerBack() {
        AgentRuntime paused = runtime(AgentProfile.State.PAUSED);
        AgentRuntime active = runtime(AgentProfile.State.ACTIVE);
        when(agents.list(ROOM)).thenReturn(List.of(paused.profile(), active.profile()));

        RoomControlView result = control.resumeAll(ROOM);

        assertThat(result.action()).isEqualTo("RESUME_ALL");
        assertThat(result.affected()).isEqualTo(1);
        verify(agents).setState(paused.agentId(), AgentProfile.State.ACTIVE);
        verify(agents, never()).setState(eq(active.agentId()), any());
    }

    /** v0.0.30 🍊 A runtime of one agent, registered with the mocked manager (no consciousness needed here). */
    private AgentRuntime runtime(AgentProfile.State state) {
        AgentId agentId = IdGen.newAgentId();
        Instant now = Instant.now();
        AgentProfile profile = new AgentProfile(agentId, ROOM, "Yuzu", "yuzu", "#ffcf33", Role.PROJECT_MANAGER,
                "Project manager", "Runs the room", "Calm",
                new PermissionScope(EnumSet.noneOf(Permission.class), Limits.defaults()), state, 0, now, now);
        AgentRuntime runtime = new AgentRuntime(profile, null);
        when(runtimes.require(agentId)).thenReturn(runtime);
        when(runtimes.find(agentId)).thenReturn(Optional.of(runtime));
        when(agents.require(agentId)).thenReturn(profile);
        return runtime;
    }

    /** v0.0.30 🍊 A streamed free-text call against the scripted provider. */
    private LlmCall streamCall() {
        return new LlmCall(new ProviderEndpoint(server.baseUrl(), "sk-test-key"),
                new TierSettings("gpt-4.1-mini", null, 100), List.of(LlmMessage.user("write a long answer")), null,
                null, null, true, null, null);
    }
}
