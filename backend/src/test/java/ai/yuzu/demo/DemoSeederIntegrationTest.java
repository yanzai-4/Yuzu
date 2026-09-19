package ai.yuzu.demo;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.Permission;
import ai.yuzu.sim.email.FakeMailbox;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.TestRooms;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v0.0.29 🍊 The demo seed: four role-matched coworkers plus simulated-world data, seeded idempotently. */
@IntegrationTest
@AutoConfigureMockMvc
class DemoSeederIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DemoSeeder seeder;

    @Autowired
    private AgentService agents;

    @Autowired
    private FakeMailbox mailbox;

    private String room;

    /** v0.0.29 🍊 A fresh empty room for every test. */
    @BeforeEach
    void setUp() {
        room = TestRooms.create(jdbc);
    }

    /** v0.0.29 🍊 Seeding hires Yuzu, Lime, Kumquat and Pomelo with the permissions their roles need. */
    @Test
    void seedsFourCoworkersWithRoleScopes() throws Exception {
        JsonNode seeded = mapper.readTree(mvc.perform(post("/api/demo/seed").param("roomId", room))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(seeded).hasSize(4);
        assertThat(field(seeded, "name")).containsExactly("Yuzu", "Lime", "Kumquat", "Pomelo");
        assertThat(field(seeded, "role"))
                .containsExactly("PROJECT_MANAGER", "RESEARCHER", "ENGINEER", "CUSTOMER_LIAISON");

        AgentProfile pm = byName("Yuzu");
        assertThat(pm.scope().has(Permission.TASK_ASSIGN)).isTrue();
        assertThat(pm.scope().has(Permission.TASK_APPROVE)).isTrue();
        assertThat(pm.scope().has(Permission.CODE_EXECUTE)).isFalse();

        assertThat(byName("Lime").scope().has(Permission.WEB_BROWSE)).isTrue();
        assertThat(byName("Kumquat").scope().has(Permission.CODE_EXECUTE)).isTrue();
        assertThat(byName("Kumquat").scope().has(Permission.TASK_ASSIGN)).isFalse();

        AgentProfile liaison = byName("Pomelo");
        assertThat(liaison.scope().has(Permission.EMAIL_SEND)).isTrue();
        assertThat(liaison.scope().limits().emailAllowedDomains()).contains("acme.test", "example.com");
    }

    /** v0.0.29 🍊 The simulated world the demo needs is there: seeded mailbox contacts and market symbols. */
    @Test
    void seedsTheSimulatedWorld() {
        seeder.seed(room);

        AgentProfile liaison = byName("Pomelo");
        assertThat(mailbox.inbox(liaison.agentId())).hasSize(5);
        assertThat(mailbox.inbox(liaison.agentId())).extracting("from")
                .contains("dana.kim@acme.test", "procurement@example.com");
        assertThat(seeder.symbols()).contains("CITR", "YUZU");
    }

    /** v0.0.29 🍊 Seeding twice changes nothing: same agents, same e-mails, edits made in between survive. */
    @Test
    void seedingIsIdempotent() throws Exception {
        List<AgentProfile> first = seeder.seed(room);
        AgentProfile engineer = byName("Kumquat");
        mvc.perform(patch("/api/agents/" + engineer.agentId().value()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"persona\":\"Edited by a human.\"}")).andExpect(status().isOk());
        int inboxBefore = mailbox.inbox(byName("Pomelo").agentId()).size();

        List<AgentProfile> second = seeder.seed(room);

        assertThat(second).extracting(a -> a.agentId().value())
                .containsExactlyElementsOf(first.stream().map(a -> a.agentId().value()).toList());
        assertThat(agents.list(room)).hasSize(4);
        assertThat(byName("Kumquat").persona()).isEqualTo("Edited by a human.");
        assertThat(mailbox.inbox(byName("Pomelo").agentId())).hasSize(inboxBefore);
    }

    /** v0.0.29 🍊 A demo agent by citrus name. */
    private AgentProfile byName(String name) {
        return agents.list(room).stream().filter(a -> a.name().equals(name)).findFirst().orElseThrow();
    }

    /** v0.0.29 🍊 One field of every element of a JSON array, in order. */
    private static List<String> field(JsonNode array, String name) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.get(name).asText()));
        return values;
    }
}
