package ai.yuzu.chat;

import ai.yuzu.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v0.0.5 🍊 End-to-end REST test of joining, posting with mentions, history and bootstrap. */
@IntegrationTest
@AutoConfigureMockMvc
class ChatIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    /** v0.0.5 🍊 Join (idempotent), post with a mention, read history and bootstrap. */
    @Test
    void joinPostAndBootstrap() throws Exception {
        JsonNode alice = json(mvc.perform(post("/api/session/join").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"Alice\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode again = json(mvc.perform(post("/api/session/join").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"Alice\"}")).andReturn().getResponse().getContentAsString());
        assertThat(again.get("id").asText()).isEqualTo(alice.get("id").asText());
        JsonNode bob = json(mvc.perform(post("/api/session/join").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"Bob\"}")).andReturn().getResponse().getContentAsString());

        JsonNode msg = json(mvc.perform(post("/api/rooms/room-0001/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + bob.get("id").asText() + "\",\"content\":\"@Alice can you review?\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(msg.get("id").asText()).matches("^msg-0000-[0-9a-f]{10}$");
        assertThat(msg.get("mentions").get(0).asText()).isEqualTo(alice.get("id").asText());
        assertThat(msg.get("time").asText()).contains(":");

        JsonNode history = json(mvc.perform(get("/api/rooms/room-0001/messages")).andReturn().getResponse().getContentAsString());
        assertThat(history.get(history.size() - 1).get("content").asText()).isEqualTo("@Alice can you review?");

        JsonNode snapshot = json(mvc.perform(get("/api/bootstrap?roomId=room-0001")).andReturn().getResponse().getContentAsString());
        assertThat(snapshot.get("roomName").asText()).isEqualTo("Citrus HQ");
        assertThat(snapshot.get("users").size()).isGreaterThanOrEqualTo(2);
        assertThat(snapshot.get("messages").size()).isGreaterThanOrEqualTo(1);
        assertThat(snapshot.get("eventCursor").asLong()).isPositive();
    }

    /** v0.0.5 🍊 Invalid usernames and the reserved word are rejected with BAD_REQUEST. */
    @Test
    void invalidUsernames() throws Exception {
        String body = mvc.perform(post("/api/session/join").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"all\"}")).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(json(body).get("code").asText()).isEqualTo("BAD_REQUEST");
        mvc.perform(post("/api/session/join").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"<script>\"}"))
                .andExpect(status().isBadRequest());
    }

    /** v0.0.5 🍊 Posting as an unknown user is NOT_FOUND. */
    @Test
    void unknownUser() throws Exception {
        mvc.perform(post("/api/rooms/room-0001/messages").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"user-ffff\",\"content\":\"hi\"}")).andExpect(status().isNotFound());
    }

    private JsonNode json(String body) throws Exception {
        return mapper.readTree(body);
    }
}
