package ai.yuzu.monitor;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.SseRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v0.0.12 🍊 Monitor in the full application: MySQL persistence, SSE events, bootstrap statuses and agent lifecycle. */
@IntegrationTest
@AutoConfigureMockMvc
class MonitorIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MonitorService monitor;

    @Autowired
    private ModuleEventStore store;

    @Autowired
    private AgentService agents;

    @Autowired
    private SseHub hub;

    /** v0.0.12 🍊 Events are written asynchronously in batches (flush and count) and streamed as module.event. */
    @Test
    void eventsArePersistedAsynchronouslyAndPublished() throws Exception {
        String room = newRoom();
        AgentId agent = hire(room).agentId();
        SseRecorder recorder = new SseRecorder(mapper);
        hub.connect(room, 0, recorder);
        store.flush();
        long before = countEvents(agent);

        Span span = monitor.start(agent, ModuleKind.TOOL, "Searching the web for yuzu prices", null, null);
        span.state("Reading 3 pages");
        span.detail("query", "yuzu price 2026");
        span.end("Found 3 sources");
        for (int i = 0; i < 200; i++) {
            monitor.info(agent, ModuleKind.SUBCONSCIOUS, "Noticed detail " + i, Map.of("i", i));
        }
        monitor.info(agent, ModuleKind.CHAT, "é🍊".repeat(700), null);
        store.flush();

        assertThat(countEvents(agent) - before).isEqualTo(204);
        assertThat(store.dropped()).isZero();
        Map<String, Object> end = jdbc.sql("""
                        SELECT module, phase, text, detail, trace_id, span_id FROM module_event
                        WHERE agent_id = :agentId AND span_id = :span AND phase = 'END'
                        """)
                .param("agentId", agent.value()).param("span", span.spanId()).query().singleRow();
        assertThat(end.get("module")).isEqualTo("TOOL");
        assertThat(end.get("text")).isEqualTo("Found 3 sources");
        assertThat(end.get("trace_id")).isEqualTo(span.traceId());
        JsonNode detail = mapper.readTree(String.valueOf(end.get("detail")));
        assertThat(detail.get("query").asText()).isEqualTo("yuzu price 2026");
        assertThat(detail.get("durationMs").isIntegralNumber()).isTrue();
        String longest = jdbc.sql("SELECT text FROM module_event WHERE agent_id = :agentId AND module = 'CHAT'")
                .param("agentId", agent.value()).query(String.class).single();
        assertThat(longest.length()).isLessThanOrEqualTo(1_000);
        assertThat(longest).startsWith("é🍊").endsWith("…");

        recorder.await("span events streamed", () -> recorder.data(EventType.MODULE_EVENT, agent.value()).size() >= 3);
        List<JsonNode> streamed = recorder.data(EventType.MODULE_EVENT, agent.value());
        assertThat(streamed.subList(0, 3)).extracting(node -> node.get("phase").asText())
                .containsExactly("START", "STATE", "END");
        assertThat(streamed.get(0).get("spanId").asText()).isEqualTo(span.spanId());
    }

    /** v0.0.12 🍊 The bootstrap snapshot carries the live status from the board, and agent.status reaches the room. */
    @Test
    void bootstrapAndStreamShowTheLiveStatus() throws Exception {
        String room = newRoom();
        AgentId agent = hire(room).agentId();
        SseRecorder recorder = new SseRecorder(mapper);
        hub.connect(room, 0, recorder);

        Span span = monitor.start(agent, ModuleKind.MAIN, "Planning the product launch", null, null);
        JsonNode status = statusIn(bootstrap(room), agent);
        assertThat(status.get("state").asText()).isEqualTo("THINKING");
        assertThat(status.get("activeModules").toString()).isEqualTo("[\"MAIN\"]");
        assertThat(status.get("bubble").get("module").asText()).isEqualTo("Main consciousness");
        assertThat(status.get("bubble").get("summary").asText()).isEqualTo("Planning the product launch");
        recorder.await("THINKING streamed", () -> recorder.data(EventType.AGENT_STATUS, agent.value()).stream()
                .anyMatch(s -> s.get("state").asText().equals("THINKING")));

        span.end("Launch plan ready");
        JsonNode idle = statusIn(bootstrap(room), agent);
        assertThat(idle.get("state").asText()).isEqualTo("IDLE");
        assertThat(idle.get("bubble").get("summary").asText()).isEqualTo(BubbleText.IDLE_SUMMARY);
        recorder.await("IDLE streamed", () -> {
            List<JsonNode> statuses = recorder.data(EventType.AGENT_STATUS, agent.value());
            return statuses.get(statuses.size() - 1).get("state").asText().equals("IDLE");
        });
    }

    /** v0.0.12 🍊 Pause and resume are reflected by the REST answers and the snapshot and recorded as SYSTEM events. */
    @Test
    void pauseAndResumeAreReflectedAndRecorded() throws Exception {
        String room = newRoom();
        AgentProfile profile = hire(room);
        String id = profile.agentId().value();

        JsonNode paused = mapper.readTree(mvc.perform(post("/api/agents/" + id + "/pause")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(paused.get("state").asText()).isEqualTo("PAUSED");
        assertThat(paused.get("bubble").get("module").asText()).isEqualTo(BubbleText.PAUSED_MODULE);
        assertThat(statusIn(bootstrap(room), profile.agentId()).get("state").asText()).isEqualTo("PAUSED");

        JsonNode resumed = mapper.readTree(mvc.perform(post("/api/agents/" + id + "/resume"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(resumed.get("state").asText()).isEqualTo("IDLE");
        JsonNode interrupted = mapper.readTree(mvc.perform(post("/api/agents/" + id + "/interrupt"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(interrupted.get("agentId").asText()).isEqualTo(id);

        store.flush();
        List<String> system = jdbc.sql("""
                        SELECT text FROM module_event WHERE agent_id = :agentId AND module = 'SYSTEM' ORDER BY seq
                        """)
                .param("agentId", id).query(String.class).list();
        // v0.0.30 🍊 Pause and interrupt also report a SYSTEM span that ends CANCELLED, so the trace shows
        // exactly what a human stopped and when.
        assertThat(system).containsExactly("Joined the team as " + profile.title() + ".", "Paused.",
                "Pause requested by a human.", "Stopped: Paused by a human", "Resumed.",
                "Interrupt requested by a human.", "Stopped: Interrupted by a human");
    }

    /** v0.0.12 🍊 Hires a researcher into the room. */
    private AgentProfile hire(String room) {
        return agents.create(room, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null));
    }

    /** v0.0.12 🍊 Creates a fresh room so the 8-agent limit of shared rooms never interferes. */
    private String newRoom() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String room = IdGen.newRoomId();
            try {
                jdbc.sql("INSERT INTO room (id, name, created_at) VALUES (:id, 'Monitor test', UTC_TIMESTAMP(3))")
                        .param("id", room).update();
                return room;
            } catch (DuplicateKeyException e) {
                // try another random id
            }
        }
        throw new IllegalStateException("Could not create a test room");
    }

    /** v0.0.12 🍊 Number of persisted events of the agent. */
    private long countEvents(AgentId agent) {
        return jdbc.sql("SELECT COUNT(*) FROM module_event WHERE agent_id = :agentId")
                .param("agentId", agent.value()).query(Long.class).single();
    }

    /** v0.0.12 🍊 GET /api/bootstrap for the room. */
    private JsonNode bootstrap(String room) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/bootstrap").param("roomId", room)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** v0.0.12 🍊 The agent's entry in the snapshot's statuses. */
    private static JsonNode statusIn(JsonNode snapshot, AgentId agent) {
        for (JsonNode status : snapshot.get("statuses")) {
            if (status.get("agentId").asText().equals(agent.value())) {
                return status;
            }
        }
        throw new AssertionError("No status for " + agent + " in " + snapshot.get("statuses"));
    }
}
