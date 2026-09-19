package ai.yuzu.monitor;

import ai.yuzu.common.concurrent.AsyncRunner;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import ai.yuzu.support.SseRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** v0.0.12 🍊 Test wiring of a status board over a real SSE hub, virtual-thread runner and timer (no Spring, no MySQL). */
final class BoardFixture implements AutoCloseable {

    static final String ROOM = "room-0c0c";

    final ObjectMapper mapper = new ObjectMapper();
    final NaturalTime time = new NaturalTime(Clock.systemUTC(), ZoneId.of("America/Los_Angeles"));
    final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    final SseHub hub = new SseHub(mapper, time, timer);
    final List<Throwable> asyncFailures = new CopyOnWriteArrayList<>();
    final AsyncRunner runner = new AsyncRunner(Executors.newVirtualThreadPerTaskExecutor(),
            List.of((context, agentId, error) -> asyncFailures.add(error)));
    final Map<AgentId, AgentLookup.Placement> placements = new ConcurrentHashMap<>();
    final SseRecorder recorder = new SseRecorder(mapper);
    final AgentStatusBoard board;

    /** v0.0.12 🍊 Builds the board with the given summarizer and timings and listens to the room stream. */
    BoardFixture(BubbleSummarizer summarizer, MonitorTimings timings) {
        board = new AgentStatusBoard(id -> Optional.ofNullable(placements.get(id)), hub, time, runner, timer,
                () -> summarizer, timings);
        hub.connect(ROOM, 0, recorder);
    }

    /** v0.0.12 🍊 A new active agent known to the registry (not yet tracked by the board). */
    AgentId agent() {
        AgentId agentId = IdGen.newAgentId();
        placements.put(agentId, new AgentLookup.Placement(ROOM, false, false));
        return agentId;
    }

    /** v0.0.12 🍊 A module event as a span of the agent would report it. */
    ModuleEvent event(AgentId agentId, ModuleKind module, EventPhase phase, String spanId, String text) {
        return new ModuleEvent(IdGen.recordId(DataName.EVENT, agentId), agentId, module, phase, text, null,
                "trace-0000-0000000001", spanId, null, time.nowInstant());
    }

    /** v0.0.12 🍊 agent.status payloads received for the agent, oldest first. */
    List<JsonNode> statuses(AgentId agentId) {
        return recorder.data(EventType.AGENT_STATUS, agentId.value());
    }

    /** v0.0.12 🍊 State of the last agent.status received for the agent ("" when none). */
    String lastPublishedState(AgentId agentId) {
        List<JsonNode> received = statuses(agentId);
        return received.isEmpty() ? "" : received.get(received.size() - 1).get("state").asText();
    }

    /** v0.0.12 🍊 Stops the hub and the timer. */
    @Override
    public void close() {
        hub.shutdown();
        timer.shutdownNow();
    }
}
