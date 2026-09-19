package ai.yuzu.trace;

import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.common.concurrent.CancelToken;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.llm.LlmCallContext;
import ai.yuzu.llm.LlmCallRecorder;
import ai.yuzu.llm.ModelTier;
import ai.yuzu.llm.provider.LlmMessage;
import ai.yuzu.llm.provider.LlmRequest;
import ai.yuzu.llm.provider.LlmResult;
import ai.yuzu.llm.provider.ResponseFormat;
import ai.yuzu.llm.usage.Usage;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.TestRooms;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v0.0.30 🍊 Raw-request inspection: the model calls of a trace and the exact JSON of one of them. */
@IntegrationTest
@AutoConfigureMockMvc
class LlmCallInspectionIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AgentService agents;

    @Autowired
    private LlmCallRecorder recorder;

    /** v0.0.30 🍊 A recorded attempt shows up under its trace and serves its raw request and response. */
    @Test
    void traceListsItsModelCallsAndServesTheRawPayload() throws Exception {
        String room = TestRooms.create(jdbc);
        AgentId agent = agents.create(room, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null))
                .agentId();
        String traceId = "trace-" + agent.hex() + "-0000000001";
        LlmCallContext ctx = new LlmCallContext(agent, "CHAT", ModelTier.DEFAULT, traceId, new CancelToken());
        LlmRequest request = new LlmRequest("gpt-4.1-mini", List.of(LlmMessage.user("hi there")),
                ResponseFormat.NONE, 256, "max_tokens", null, null, null, false, false, null);
        LlmResult result = new LlmResult("hello back", "stop", null, new Usage(120, 64, 0, 7, 0, false, true),
                "gpt-4.1-mini", 314, 42L, "{\"model\":\"gpt-4.1-mini\",\"messages\":[{\"role\":\"user\","
                + "\"content\":\"hi there\"}]}", "{\"choices\":[{\"message\":{\"content\":\"hello back\"}}]}");
        recorder.success(ctx, "TEXT", request, result, 1);
        recorder.flush();

        JsonNode calls = getJson("/api/traces/" + traceId + "/llm-calls");
        assertThat(calls).hasSize(1);
        JsonNode call = calls.get(0);
        assertThat(call.get("agentId").asText()).isEqualTo(agent.value());
        assertThat(call.get("module").asText()).isEqualTo("CHAT");
        assertThat(call.get("tier").asText()).isEqualTo("DEFAULT");
        assertThat(call.get("model").asText()).isEqualTo("gpt-4.1-mini");
        assertThat(call.get("status").asText()).isEqualTo("OK");
        assertThat(call.get("promptTokens").asInt()).isEqualTo(120);
        assertThat(call.get("cachedTokens").asInt()).isEqualTo(64);
        assertThat(call.get("latencyMs").asLong()).isEqualTo(314);
        assertThat(call.get("ttftMs").asLong()).isEqualTo(42);
        assertThat(call.get("hasPayload").asBoolean()).isTrue();

        JsonNode payload = awaitPayload(call.get("id").asText());
        assertThat(payload.get("request").get("messages").get(0).get("content").asText()).isEqualTo("hi there");
        assertThat(payload.get("response").get("choices").get(0).get("message").get("content").asText())
                .isEqualTo("hello back");
        assertThat(payload.toString()).doesNotContain("sk-");
    }

    /** v0.0.30 🍊 Unknown ids answer cleanly instead of failing. */
    @Test
    void unknownTraceAndCallAnswerCleanly() throws Exception {
        assertThat(getJson("/api/traces/trace-0000-0000000000/llm-calls")).isEmpty();
        mvc.perform(get("/api/llm-calls/llm-0000-0000000000/payload")).andExpect(status().isNotFound());
        mvc.perform(get("/api/traces/not a trace id/llm-calls")).andExpect(status().isBadRequest());
    }

    /** v0.0.30 🍊 Polls the payload endpoint: the file is written on a virtual thread right after the row. */
    private JsonNode awaitPayload(String callId) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            var response = mvc.perform(get("/api/llm-calls/" + callId + "/payload")).andReturn().getResponse();
            if (response.getStatus() == 200) {
                return mapper.readTree(response.getContentAsString());
            }
            Thread.sleep(20);
        }
        throw new AssertionError("The payload of " + callId + " was never written.");
    }

    /** v0.0.30 🍊 GETs and parses the JSON answer. */
    private JsonNode getJson(String path) throws Exception {
        return mapper.readTree(mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
