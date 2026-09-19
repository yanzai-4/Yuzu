package ai.yuzu.trace;

import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.monitor.ModuleKind;
import ai.yuzu.monitor.MonitorService;
import ai.yuzu.monitor.Span;
import ai.yuzu.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v0.0.12 🍊 Trace REST API: cross-agent traces in time order, agent history paging, contract shapes and errors. */
@IntegrationTest
@AutoConfigureMockMvc
class TraceIntegrationTest {

    private static final Set<String> CONTRACT_FIELDS = Set.of("id", "agentId", "module", "phase", "text", "detail",
            "traceId", "spanId", "parentSpanId", "time");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MonitorService monitor;

    @Autowired
    private AgentService agents;

    /** v0.0.12 🍊 A trace spanning two agents comes back complete and in time order. */
    @Test
    void traceSpansAgentsInTimeOrder() throws Exception {
        String room = newRoom();
        AgentId pm = hire(room, Role.PROJECT_MANAGER);
        AgentId researcher = hire(room, Role.RESEARCHER);

        Span ask = monitor.start(pm, ModuleKind.MAIN, "Deciding to ask Lime for sales data", null, null);
        Span query = monitor.start(researcher, ModuleKind.TOOL, "Querying the sales table", ask.traceId(),
                ask.spanId());
        monitor.info(pm, ModuleKind.SUBCONSCIOUS, "Unrelated thought", null);
        query.end("Found 42 rows");
        ask.end("Report sent to Alice");

        JsonNode trace = getJson("/api/traces/" + ask.traceId());
        assertThat(trace).hasSize(4);
        List<String> order = new ArrayList<>();
        trace.forEach(e -> order.add(e.get("agentId").asText() + " " + e.get("phase").asText()));
        assertThat(order).containsExactly(pm.value() + " START", researcher.value() + " START",
                researcher.value() + " END", pm.value() + " END");
        assertThat(trace.get(1).get("parentSpanId").asText()).isEqualTo(ask.spanId());
        assertThat(trace.get(1).get("traceId").asText()).isEqualTo(ask.traceId());
        assertThat(getJson("/api/traces/trace-0000-0000000000")).isEmpty();
    }

    /** v0.0.12 🍊 An agent's history is newest first, limited, and pages backwards with beforeSeq. */
    @Test
    void agentEventsAreNewestFirstAndPageBySeq() throws Exception {
        String room = newRoom();
        AgentId agent = hire(room, Role.ENGINEER);
        for (int i = 1; i <= 5; i++) {
            monitor.info(agent, ModuleKind.MAIN, "event " + i, Map.of("i", i));
        }

        JsonNode latest = getJson("/api/agents/" + agent.value() + "/events?limit=3");
        assertThat(texts(latest)).containsExactly("event 5", "event 4", "event 3");
        long seqOfThird = jdbc.sql("SELECT seq FROM module_event WHERE agent_id = :agentId AND text = 'event 3'")
                .param("agentId", agent.value()).query(Long.class).single();
        JsonNode older = getJson("/api/agents/" + agent.value() + "/events?limit=3&beforeSeq=" + seqOfThird);
        assertThat(texts(older)).containsExactly("event 2", "event 1", "Joined the team as Software Engineer.");
        assertThat(getJson("/api/agents/" + agent.value() + "/events")).hasSize(6);
        assertThat(getJson("/api/agents/" + agent.value() + "/events?limit=0")).hasSize(1);
    }

    /** v0.0.12 🍊 Events have exactly the contract fields; optional ones are omitted when empty. */
    @Test
    void restShapesMatchTheContract() throws Exception {
        String room = newRoom();
        AgentId agent = hire(room, Role.CUSTOMER_LIAISON);
        try (Span span = monitor.start(agent, ModuleKind.CHAT, "Reading Bob's message", null, null)) {
            span.detail("messageId", "msg-0000-0123456789");
        }
        JsonNode events = getJson("/api/agents/" + agent.value() + "/events?limit=3");
        assertThat(events).hasSize(3);
        for (JsonNode event : events) {
            List<String> fields = new ArrayList<>();
            event.fieldNames().forEachRemaining(fields::add);
            assertThat(CONTRACT_FIELDS).containsAll(fields);
            assertThat(fields).contains("id", "agentId", "module", "phase", "text", "time");
            assertThat(event.get("id").asText()).matches("^event-" + agent.hex() + "-[0-9a-f]{10}$");
            assertThat(event.get("time").asText()).matches("^[A-Z][a-z]{2} [A-Z][a-z]{2} \\d{1,2}, \\d{1,2}:\\d{2}:\\d{2} [AP]M$");
        }
        JsonNode end = events.get(0);
        assertThat(end.get("phase").asText()).isEqualTo("END");
        assertThat(end.get("text").asText()).isEqualTo("done");
        assertThat(end.get("detail").get("messageId").asText()).isEqualTo("msg-0000-0123456789");
        assertThat(end.get("detail").get("durationMs").isIntegralNumber()).isTrue();
        assertThat(end.has("parentSpanId")).isFalse();
        JsonNode joined = events.get(2);
        assertThat(joined.get("module").asText()).isEqualTo("SYSTEM");
        assertThat(joined.get("phase").asText()).isEqualTo("INFO");
        assertThat(joined.has("traceId")).isFalse();
        assertThat(joined.has("spanId")).isFalse();
    }

    /** v0.0.12 🍊 Malformed ids are BAD_REQUEST, unknown agents NOT_FOUND. */
    @Test
    void badRequestsAreRejected() throws Exception {
        assertThat(errorCode("/api/agents/not-an-agent/events", 400)).isEqualTo("BAD_REQUEST");
        AgentId stranger = IdGen.newAgentId();
        while (agents.find(stranger).isPresent()) {
            stranger = IdGen.newAgentId();
        }
        assertThat(errorCode("/api/agents/" + stranger.value() + "/events", 404)).isEqualTo("NOT_FOUND");
        assertThat(errorCode("/api/traces/bad!id", 400)).isEqualTo("BAD_REQUEST");
        assertThat(errorCode("/api/traces/" + "x".repeat(40), 400)).isEqualTo("BAD_REQUEST");
    }

    /** v0.0.12 🍊 GET that must succeed, parsed. */
    private JsonNode getJson(String url) throws Exception {
        return mapper.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    /** v0.0.12 🍊 GET that must fail with the status; returns the ApiError code. */
    private String errorCode(String url, int httpStatus) throws Exception {
        return mapper.readTree(mvc.perform(get(url)).andExpect(status().is(httpStatus)).andReturn().getResponse()
                .getContentAsString()).get("code").asText();
    }

    /** v0.0.12 🍊 Texts of a JSON array of events. */
    private static List<String> texts(JsonNode events) {
        List<String> texts = new ArrayList<>();
        events.forEach(e -> texts.add(e.get("text").asText()));
        return texts;
    }

    /** v0.0.12 🍊 Hires an agent into the room. */
    private AgentId hire(String room, Role role) {
        return agents.create(room, new CreateAgentRequest(role, null, null, null, null, null)).agentId();
    }

    /** v0.0.12 🍊 Creates a fresh room so the 8-agent limit of shared rooms never interferes. */
    private String newRoom() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String room = IdGen.newRoomId();
            try {
                jdbc.sql("INSERT INTO room (id, name, created_at) VALUES (:id, 'Trace test', UTC_TIMESTAMP(3))")
                        .param("id", room).update();
                return room;
            } catch (DuplicateKeyException e) {
                // try another random id
            }
        }
        throw new IllegalStateException("Could not create a test room");
    }
}
