package ai.yuzu.module;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.common.error.LlmOutputInvalidException;
import ai.yuzu.internal.memory.WmCompactorModule;
import ai.yuzu.internal.memory.WorkingMemoryEntry;
import ai.yuzu.settings.LlmProvider;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.support.FakeLlmServer;
import ai.yuzu.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.14 🍊 The AI module template end to end: handbook + module template + segments + time, retries, errors. */
@IntegrationTest
class AiModuleIntegrationTest {

    @Autowired
    private WmCompactorModule compactor;
    @Autowired
    private AgentService agents;
    @Autowired
    private SettingsService settings;
    @Autowired
    private ObjectMapper mapper;

    private FakeLlmServer server;
    private AgentProfile agent;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeLlmServer();
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-5678");
        agent = agents.create("room-0001", new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null));
    }

    @AfterEach
    void tearDown() {
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
        agents.retire(agent.agentId());
    }

    /** v0.0.14 🍊 The prompt has the handbook + template in system and the segments + time last in user. */
    @Test
    void composesCacheOrderedPrompt() throws Exception {
        server.enqueue(200, FakeLlmServer.completion("{\"summary\":\"Alice asked me for a report.\"}", 1300, 1024, 12));
        String digest = compactor.compact(agent.agentId(), "", List.of(entry("Please write the report")));
        assertThat(digest).isEqualTo("Alice asked me for a report.");
        JsonNode body = mapper.readTree(server.requests().getFirst());
        String system = body.get("messages").get(0).get("content").asText();
        String user = body.get("messages").get(1).get("content").asText();
        assertThat(system).startsWith("## Company handbook").contains("WORKING-MEMORY COMPACTOR");
        assertThat(user).contains("## Entries to fold into the summary").contains("Please write the report");
        assertThat(user.substring(user.lastIndexOf("## "))).startsWith("## Current time");
        assertThat(body.get("response_format").get("type").asText()).isEqualTo("json_schema");
    }

    /** v0.0.14 🍊 Four invalid answers surface as LLM_OUTPUT_INVALID (the compactor has no fallback). */
    @Test
    void invalidOutputSurfaces() {
        for (int i = 0; i < 4; i++) {
            server.enqueue(200, FakeLlmServer.completion("{\"nope\":1}", 100, 0, 3));
        }
        assertThatThrownBy(() -> compactor.compact(agent.agentId(), "", List.of(entry("x"))))
                .isInstanceOf(LlmOutputInvalidException.class);
    }

    private WorkingMemoryEntry entry(String text) {
        return new WorkingMemoryEntry("wm-0000-0000000001", agent.agentId().value(), 1, "run-1",
                WorkingMemoryEntry.Direction.IN, "Alice (human) told me in the group chat", "pool-x", text, 5, false,
                Instant.now());
    }
}
