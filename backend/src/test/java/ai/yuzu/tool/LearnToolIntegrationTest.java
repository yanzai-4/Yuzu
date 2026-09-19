package ai.yuzu.tool;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.internal.cognition.HabitIndexService;
import ai.yuzu.internal.memory.MemoryConflictRepository;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.settings.LlmProvider;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.support.FakeLlmServer;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.TestRooms;
import ai.yuzu.tool.impl.learn.LearnTool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolRegistry;
import ai.yuzu.tool.spi.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static ai.yuzu.support.FakeLlmServer.completion;
import static ai.yuzu.support.FakeLlmServer.schema;
import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.28 🍊 The learn tool hands what the main consciousness wants to learn to the learning module. */
@IntegrationTest
class LearnToolIntegrationTest {

    @Autowired
    private AgentService agents;
    @Autowired
    private AgentRuntimeManager runtimes;
    @Autowired
    private SettingsService settings;
    @Autowired
    private LearnTool learn;
    @Autowired
    private ToolRegistry registry;
    @Autowired
    private HabitIndexService habitIndex;
    @Autowired
    private MemoryConflictRepository conflicts;
    @Autowired
    private ModuleDeps deps;
    @Autowired
    private NaturalTime time;
    @Autowired
    private JdbcClient jdbc;

    private FakeLlmServer server;
    private AgentProfile kumquat;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeLlmServer();
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-9999");
        String roomId = TestRooms.create(jdbc);
        kumquat = agents.createNamed(roomId, new CreateAgentRequest(Role.ENGINEER, null, null, null, null, null),
                "Kumquat");
    }

    @AfterEach
    void tearDown() {
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.28 🍊 The tool is registered and every coworker may learn from its own work. */
    @Test
    void theToolIsAvailableToEveryCoworker() {
        assertThat(registry.find("learn")).isPresent();
        assertThat(registry.permittedTools(kumquat.scope())).contains("learn:");
        assertThat(learn.spec().trusted()).isTrue();
    }

    /** v0.0.28 🍊 A new habit is written and the cognition index picks it up. */
    @Test
    void learningANewHabitWritesHabitMemory() {
        ToolResult result = learn.execute(context(), new LearnTool.Args("Run the tests before pushing",
                "when I finish a code change", "run the unit tests in the sandbox before I report the change"));

        assertThat(result.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(result.output()).contains("Run the tests before pushing");
        assertThat(rows()).isEqualTo(1);
        assertThat(habitIndex.index(kumquat.agentId())).contains("Run the tests before pushing");
        assertThat(server.requests()).isEmpty();
    }

    /** v0.0.28 🍊 The tool goes through the same de-duplication as the subconscious. */
    @Test
    void learningTheSameHabitTwiceIsDeduplicated() {
        LearnTool.Args args = new LearnTool.Args("Run the tests before pushing", "when I finish a code change",
                "run the unit tests in the sandbox before I report the change");
        learn.execute(context(), args);
        ToolResult again = learn.execute(context(), args);

        assertThat(again.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(again.output()).containsIgnoringCase("already");
        assertThat(rows()).isEqualTo(1);
    }

    /** v0.0.28 🍊 A contradicting habit is held as a conflict instead of overwriting what the agent knows. */
    @Test
    void contradictingHabitBecomesAHeldConflict() {
        learn.execute(context(), new LearnTool.Args("Test policy", "when I finish a code change",
                "always run the tests before reporting"));
        String targetId = jdbc.sql("SELECT id FROM habit_memory WHERE agent_id = ?")
                .param(kumquat.agentId().value()).query(String.class).single();
        server.enqueueFor(schema("memory_judge"), completion("{\"reasoning\":\"scripted\",\"verdict\":\"CONFLICT\","
                + "\"targetId\":\"" + targetId + "\",\"action\":\"IGNORE\",\"mergedText\":null,"
                + "\"conflictReason\":\"the new habit skips the tests\"}", 1500, 0, 40));

        ToolResult result = learn.execute(context(), new LearnTool.Args("Test policy", "when I finish a code change",
                "skip the tests when the change is small"));

        assertThat(result.output()).containsIgnoringCase("conflict");
        assertThat(conflicts.open(kumquat.agentId())).hasSize(1);
        assertThat(jdbc.sql("SELECT technique FROM habit_memory WHERE agent_id = ? AND status = 'ACTIVE'")
                .param(kumquat.agentId().value()).query(String.class).single()).contains("always run the tests");
    }

    private ToolContext context() {
        AgentContext ctx = runtimes.require(kumquat.agentId()).context("trace-learn", null, time);
        return new ToolContext(ctx, "batch-learn", "call-learn", 0, "learn this", 0,
                deps.reporter().start(kumquat.agentId(), "TOOL", "test", "trace-learn", null));
    }

    private int rows() {
        return jdbc.sql("SELECT COUNT(*) FROM habit_memory WHERE agent_id = ?")
                .param(kumquat.agentId().value()).query(Integer.class).single();
    }
}
