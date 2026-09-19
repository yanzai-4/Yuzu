package ai.yuzu.internal;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.internal.intake.Stimulus;
import ai.yuzu.internal.planning.TaskPlannerService;
import ai.yuzu.module.TaskStateProvider;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.settings.LlmProvider;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.support.FakeLlmServer;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.task.list.TaskItemState;
import ai.yuzu.task.list.TaskList;
import ai.yuzu.task.list.TaskListService;
import ai.yuzu.task.list.TaskListStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.concurrent.ThreadLocalRandom;

import static ai.yuzu.support.FakeLlmServer.completion;
import static ai.yuzu.support.FakeLlmServer.schema;
import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.21 🍊 The planning module creates and updates the task list in code, with validation feedback. */
@IntegrationTest
class PlanningIntegrationTest {

    @Autowired
    private AgentService agents;
    @Autowired
    private HumanUserService users;
    @Autowired
    private SettingsService settings;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private TaskPlannerService planner;
    @Autowired
    private TaskListService lists;
    @Autowired
    private TaskStateProvider taskState;
    @Autowired
    private AgentRuntimeManager runtimes;
    @Autowired
    private NaturalTime time;

    private FakeLlmServer server;
    private AgentProfile lime;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeLlmServer();
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-4444");
        String roomId = String.format("room-%04x", ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        jdbc.sql("INSERT INTO room (id, name, created_at) VALUES (:id, 'Plan room', UTC_TIMESTAMP(3))")
                .param("id", roomId).update();
        lime = agents.createNamed(roomId, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null), "Lime");
        users.join(roomId, "Alice");
    }

    @AfterEach
    void tearDown() {
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.21 🍊 CREATE (after a wrong publisher is corrected), then UPDATE checks items and requests approval. */
    @Test
    void createThenUpdate() {
        server.enqueueFor(schema("planning"), plan("CREATE", "\"Compare citrus drink prices\"", "[\"Find 3 competitors\",\"Collect prices\"]", "\"Bob\"", "[]", false))
                .enqueueFor(schema("planning"), plan("CREATE", "\"Compare citrus drink prices\"", "[\"Find 3 competitors\",\"Collect prices\"]", "\"Alice\"", "[]", false));
        String note = planner.plan(ctx(), new Stimulus.NoticeStimulus("test", "Alice asked me to compare prices"),
                "Alice: @Lime please compare citrus drink prices");
        assertThat(note).contains("I started a new task list").contains("Alice will approve");
        String retry = server.requests().get(1);
        assertThat(retry).contains("publisher \\\"Bob\\\" is not a member");
        TaskList list = lists.current(lime.agentId()).current();
        assertThat(list.items()).hasSize(2);
        assertThat(taskState.current(lime.agentId())).contains("Compare citrus drink prices");

        server.enqueueFor(schema("planning"), plan("UPDATE", "null", "[]", "null",
                "[{\"op\":\"CHECK\",\"item\":1,\"text\":\"found them\",\"goal\":null},{\"op\":\"CHECK\",\"item\":2,\"text\":null,\"goal\":null}]", true));
        String update = planner.plan(ctx(), new Stimulus.ToolResultsStimulus("batch-x", "Results: prices collected"),
                "Results: prices collected");
        assertThat(update).contains("I updated my task list").contains("waits for Alice");
        TaskList after = lists.current(lime.agentId()).current();
        assertThat(after.status()).isEqualTo(TaskListStatus.AWAITING_APPROVAL);
        assertThat(after.items()).allMatch(i -> i.state() == TaskItemState.DONE);
    }

    /** v0.0.21 🍊 NONE changes nothing and returns no note. */
    @Test
    void noneChangesNothing() {
        String note = planner.plan(ctx(), new Stimulus.NoticeStimulus("test", "hello"), "Alice: good morning");
        assertThat(note).isNull();
        assertThat(lists.current(lime.agentId()).current()).isNull();
    }

    private AgentContext ctx() {
        return runtimes.require(lime.agentId()).context("trace-plan", null, time);
    }

    private static String plan(String mode, String goal, String items, String publisher, String ops, boolean approval) {
        return completion("{\"reasoning\":\"r\",\"mode\":\"" + mode + "\",\"goal\":" + goal + ",\"items\":" + items
                + ",\"publisher\":" + publisher + ",\"ticketId\":null,\"ops\":" + ops + ",\"requestApproval\":" + approval + "}", 2000, 1024, 40);
    }
}
