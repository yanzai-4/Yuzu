package ai.yuzu.agent;

import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v0.0.6 🍊 Hiring limits, citrus names, permission edits, pause/resume and retirement over REST. */
@IntegrationTest
@AutoConfigureMockMvc
class AgentIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AgentRuntimeManager runtimes;

    /** v0.0.6 🍊 Eight agents can be hired with unique citrus names; the ninth gets AGENT_LIMIT. */
    @Test
    void hiringLimitAndNames() throws Exception {
        jdbc.sql("INSERT INTO room (id, name, created_at) VALUES ('room-00aa', 'Limit room', UTC_TIMESTAMP(3))").update();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < AgentService.MAX_AGENTS; i++) {
            JsonNode agent = create("room-00aa", "RESEARCHER");
            assertThat(agent.get("agentId").asText()).matches("^agent-[0-9a-f]{4}$");
            assertThat(names.add(agent.get("name").asText())).isTrue();
            assertThat(runtimes.find(AgentId.of(agent.get("agentId").asText()))).isPresent();
        }
        String body = mvc.perform(post("/api/rooms/room-00aa/agents").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ENGINEER\"}")).andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(body).get("code").asText()).isEqualTo("AGENT_LIMIT");
    }

    /** v0.0.6 🍊 Presets fill permissions; PATCH edits them; pause/resume and retire work. */
    @Test
    void editPauseRetire() throws Exception {
        JsonNode agent = create("room-0001", "CUSTOMER_LIAISON");
        String id = agent.get("agentId").asText();
        assertThat(agent.get("permissions").toString()).contains("EMAIL_SEND");
        assertThat(agent.get("limits").get("emailAllowedDomains").toString()).contains("acme.test");

        JsonNode edited = mapper.readTree(mvc.perform(patch("/api/agents/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"CHAT_POST\",\"EMAIL_READ\"],\"limits\":{\"emailMaxPerHour\":3}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(edited.get("permissions").toString()).doesNotContain("EMAIL_SEND");
        assertThat(edited.get("limits").get("emailMaxPerHour").asInt()).isEqualTo(3);
        assertThat(edited.get("limits").get("emailAllowedDomains").toString()).contains("acme.test");

        mvc.perform(post("/api/agents/" + id + "/pause")).andExpect(status().isOk());
        assertThat(runtimes.require(AgentId.of(id)).isPaused()).isTrue();
        mvc.perform(post("/api/agents/" + id + "/resume")).andExpect(status().isOk());
        assertThat(runtimes.require(AgentId.of(id)).isPaused()).isFalse();

        mvc.perform(delete("/api/agents/" + id)).andExpect(status().isNoContent());
        assertThat(runtimes.find(AgentId.of(id))).isEmpty();
        String list = mvc.perform(get("/api/rooms/room-0001/agents")).andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(id);
    }

    /** v0.0.34 🍊 A workgroup has exactly one project manager; hiring a second one is a CONFLICT. */
    @Test
    void onlyOneProjectManagerPerWorkgroup() throws Exception {
        jdbc.sql("INSERT INTO room (id, name, created_at) VALUES ('room-00pm', 'PM room', UTC_TIMESTAMP(3))").update();
        JsonNode pm = create("room-00pm", "PROJECT_MANAGER");
        String body = mvc.perform(post("/api/rooms/room-00pm/agents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PROJECT_MANAGER\"}"))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        JsonNode error = mapper.readTree(body);
        assertThat(error.get("code").asText()).isEqualTo("CONFLICT");
        assertThat(error.get("message").asText()).contains(pm.get("name").asText());
        // Every other role is still free to hire, and the PM can be replaced after retiring it.
        create("room-00pm", "ENGINEER");
        mvc.perform(delete("/api/agents/" + pm.get("agentId").asText())).andExpect(status().isNoContent());
        assertThat(create("room-00pm", "PROJECT_MANAGER").get("role").asText()).isEqualTo("PROJECT_MANAGER");
    }

    /** v0.0.6 🍊 The presets endpoint lists all five roles. */
    @Test
    void presets() throws Exception {
        JsonNode roles = mapper.readTree(mvc.perform(get("/api/roles")).andReturn().getResponse().getContentAsString());
        assertThat(roles).hasSize(5);
        assertThat(roles.get(0).get("role").asText()).isEqualTo("PROJECT_MANAGER");
    }

    private JsonNode create(String roomId, String role) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/rooms/" + roomId + "/agents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
