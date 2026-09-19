package ai.yuzu.agent;

import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.TestRooms;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v0.0.30 🍊 Agent control REST: interrupt records CANCELLED, stop all / resume all switch a whole room. */
@IntegrationTest
@AutoConfigureMockMvc
class AgentControlIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AgentRuntimeManager runtimes;

    /** v0.0.30 🍊 Interrupt cancels the in-flight token, keeps the agent active and leaves a CANCELLED trace event. */
    @Test
    void interruptCancelsWorkAndIsVisibleInTheTrace() throws Exception {
        String room = TestRooms.create(jdbc);
        String id = hire(room, "ENGINEER");
        var before = runtimes.require(AgentId.of(id)).cancelToken();

        JsonNode status = postJson("/api/agents/" + id + "/interrupt");
        assertThat(status.get("agentId").asText()).isEqualTo(id);
        assertThat(before.isCancelled()).isTrue();
        assertThat(runtimes.require(AgentId.of(id)).cancelToken().isCancelled()).isFalse();
        assertThat(agent(room, id).get("state").asText()).isEqualTo("ACTIVE");

        JsonNode events = getJson("/api/agents/" + id + "/events?limit=20");
        List<String> phases = new ArrayList<>();
        events.forEach(e -> phases.add(e.get("phase").asText() + " " + e.get("module").asText()));
        assertThat(phases).contains("CANCELLED SYSTEM");
    }

    /** v0.0.30 🍊 Stop all pauses every coworker of the room; resume all brings them back. */
    @Test
    void stopAllAndResumeAllSwitchTheWholeRoom() throws Exception {
        String room = TestRooms.create(jdbc);
        String first = hire(room, "RESEARCHER");
        String second = hire(room, "ENGINEER");

        JsonNode stopped = postJson("/api/rooms/" + room + "/stop-all");
        assertThat(stopped.get("action").asText()).isEqualTo("STOP_ALL");
        assertThat(stopped.get("affected").asInt()).isEqualTo(2);
        assertThat(stopped.get("statuses")).hasSize(2);
        assertThat(agent(room, first).get("state").asText()).isEqualTo("PAUSED");
        assertThat(agent(room, second).get("state").asText()).isEqualTo("PAUSED");
        assertThat(runtimes.require(AgentId.of(first)).isPaused()).isTrue();

        JsonNode resumed = postJson("/api/rooms/" + room + "/resume-all");
        assertThat(resumed.get("action").asText()).isEqualTo("RESUME_ALL");
        assertThat(resumed.get("affected").asInt()).isEqualTo(2);
        assertThat(agent(room, first).get("state").asText()).isEqualTo("ACTIVE");
        assertThat(agent(room, second).get("state").asText()).isEqualTo("ACTIVE");
        assertThat(runtimes.require(AgentId.of(second)).isPaused()).isFalse();

        // Stopping an already quiet room is safe and changes nobody.
        postJson("/api/rooms/" + room + "/stop-all");
        assertThat(postJson("/api/rooms/" + room + "/stop-all").get("affected").asInt()).isZero();
    }

    /** v0.0.30 🍊 Controlling an unknown agent or room is a clean NOT_FOUND / empty result, never a 500. */
    @Test
    void unknownTargetsFailCleanly() throws Exception {
        String body = mvc.perform(post("/api/agents/agent-ffff/interrupt")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(body).get("code").asText()).isEqualTo("NOT_FOUND");
        assertThat(postJson("/api/rooms/room-fffe/stop-all").get("affected").asInt()).isZero();
    }

    /** v0.0.30 🍊 Hires one agent and returns its id. */
    private String hire(String room, String role) throws Exception {
        String body = mvc.perform(post("/api/rooms/" + room + "/agents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"" + role + "\"}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("agentId").asText();
    }

    /** v0.0.30 🍊 The agent as the room listing shows it. */
    private JsonNode agent(String room, String agentId) throws Exception {
        for (JsonNode node : getJson("/api/rooms/" + room + "/agents")) {
            if (node.get("agentId").asText().equals(agentId)) {
                return node;
            }
        }
        throw new AssertionError("Agent " + agentId + " is not in room " + room);
    }

    /** v0.0.30 🍊 POSTs and parses the JSON answer. */
    private JsonNode postJson(String path) throws Exception {
        return mapper.readTree(mvc.perform(post(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** v0.0.30 🍊 GETs and parses the JSON answer. */
    private JsonNode getJson(String path) throws Exception {
        return mapper.readTree(mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
