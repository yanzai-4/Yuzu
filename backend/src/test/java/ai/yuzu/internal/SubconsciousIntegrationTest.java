package ai.yuzu.internal;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.runtime.AgentRuntime;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.internal.consciousness.Origin;
import ai.yuzu.internal.consciousness.PoolMessage;
import ai.yuzu.settings.LlmProvider;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.support.FakeLlmServer;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.TestRooms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.function.BooleanSupplier;

import static ai.yuzu.support.FakeLlmServer.completion;
import static ai.yuzu.support.FakeLlmServer.schema;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * v0.0.28 🍊 S29: the subconscious sees only the current round, its advice reaches the pool without waking the
 * main consciousness, and its learn / remember candidates reach the long-term memories.
 */
@IntegrationTest
class SubconsciousIntegrationTest {

    @Autowired
    private AgentService agents;
    @Autowired
    private AgentRuntimeManager runtimes;
    @Autowired
    private SettingsService settings;
    @Autowired
    private JdbcClient jdbc;

    private FakeLlmServer server;
    private AgentProfile lime;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeLlmServer();
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-7777");
        String roomId = TestRooms.create(jdbc);
        lime = agents.createNamed(roomId, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null), "Lime");
    }

    @AfterEach
    void tearDown() {
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.28 🍊 Advice enters the pool as a non-trigger message, so the main consciousness never runs for it. */
    @Test
    void adviceEntersThePoolWithoutTriggeringTheMainConsciousness() {
        AgentRuntime runtime = runtimes.require(lime.agentId());
        runtime.consciousness().setPaused(true);
        server.defaultFor(schema("subconscious"), completion(output(
                "Alice always wants the sources listed, I should do that here too.", "[]", "[]"), 2000, 0, 40));

        runtime.consciousness().offer(Origin.EXTERNAL, "Alice (human) told me in the group chat",
                "Please compare the three competitor bottles.", "trace-sub", 0, false);

        await(() -> runtime.consciousness().pool().size() == 2);
        List<PoolMessage> pool = runtime.consciousness().pool().peek();
        assertThat(pool).extracting(PoolMessage::origin)
                .containsExactly(Origin.EXTERNAL, Origin.SUBCONSCIOUS);
        assertThat(pool.getLast().text()).contains("sources listed");
        assertThat(pool.getLast().isTrigger()).isFalse();
        assertThat(runtime.consciousness().pool().triggerCount()).isEqualTo(1);
        assertThat(runtime.consciousness().pool().isRunning()).isFalse();
        assertThat(server.requests()).noneMatch(b -> b.contains(schema("main")));
    }

    /** v0.0.28 🍊 Only non-subconscious messages dispatch a pass: subconscious advice never spawns another one. */
    @Test
    void subconsciousMessagesNeverSpawnAnotherPass() throws Exception {
        AgentRuntime runtime = runtimes.require(lime.agentId());
        runtime.consciousness().setPaused(true);

        runtime.consciousness().offer(Origin.SUBCONSCIOUS, "me (my own thought)", "Maybe I should double-check.",
                "trace-sub2", 0, false);

        Thread.sleep(300);
        assertThat(runtime.consciousness().pool().size()).isEqualTo(1);
        assertThat(server.requests()).isEmpty();
    }

    /** v0.0.28 🍊 learn and remember candidates are handed to the learning and memory modules. */
    @Test
    void learnAndRememberCandidatesReachLongTermMemory() {
        AgentRuntime runtime = runtimes.require(lime.agentId());
        runtime.consciousness().setPaused(true);
        server.defaultFor(schema("subconscious"), completion(output(null,
                "[{\"name\":\"Cite every source\",\"scenario\":\"when I hand Alice a research brief\","
                        + "\"technique\":\"list the page title and link under every claim\"}]",
                "[{\"title\":\"Citrus Spark launch date\",\"content\":\"The Citrus Spark bottle launches on Friday.\","
                        + "\"keywords\":[\"Citrus Spark\",\"launch\"]}]"), 2000, 0, 60));

        runtime.consciousness().offer(Origin.EXTERNAL, "Alice (human) told me in the group chat",
                "The Citrus Spark bottle launches on Friday; always cite your sources.", "trace-sub3", 0, false);

        await(() -> count("habit_memory") == 1 && count("deep_memory") == 1);
        assertThat(one("SELECT name FROM habit_memory WHERE agent_id = ?")).isEqualTo("Cite every source");
        assertThat(one("SELECT title FROM deep_memory WHERE agent_id = ?")).isEqualTo("Citrus Spark launch date");
        assertThat(one("SELECT source FROM deep_memory WHERE agent_id = ?")).isEqualTo("SUBCONSCIOUS");
        assertThat(server.requests()).noneMatch(b -> b.contains(schema("memory_judge")));
    }

    /** v0.0.28 🍊 A scripted subconscious answer. */
    private static String output(String advice, String learn, String remember) {
        return "{\"reasoning\":\"one pass\",\"advice\":" + (advice == null ? "null" : "\"" + advice + "\"")
                + ",\"learn\":" + learn + ",\"remember\":" + remember + ",\"conflictUpdates\":[]}";
    }

    private int count(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE agent_id = ?")
                .param(lime.agentId().value()).query(Integer.class).single();
    }

    private String one(String sql) {
        return jdbc.sql(sql).param(lime.agentId().value()).query(String.class).single();
    }

    private static void await(BooleanSupplier condition) {
        for (int i = 0; i < 500 && !condition.getAsBoolean(); i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        assertThat(condition.getAsBoolean()).as("condition never became true").isTrue();
    }
}
