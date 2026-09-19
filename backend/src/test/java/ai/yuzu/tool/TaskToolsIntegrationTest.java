package ai.yuzu.tool;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Permission;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.room.UserView;
import ai.yuzu.settings.LlmProvider;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.support.FakeLlmServer;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.task.Actor;
import ai.yuzu.task.list.TaskList;
import ai.yuzu.task.list.TaskListService;
import ai.yuzu.task.list.TaskOp;
import ai.yuzu.task.ticket.NewTicket;
import ai.yuzu.task.ticket.Ticket;
import ai.yuzu.task.ticket.TicketService;
import ai.yuzu.task.ticket.TicketStatus;
import ai.yuzu.tool.impl.task.TaskApproveTool;
import ai.yuzu.tool.impl.task.TicketAssignTool;
import ai.yuzu.tool.impl.task.TicketCreateTool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static ai.yuzu.support.FakeLlmServer.completion;
import static ai.yuzu.support.FakeLlmServer.schema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.22 🍊 PM ticket tools enforce permissions, resolve room names, and notify assignees through intake. */
@IntegrationTest
class TaskToolsIntegrationTest {

    @Autowired
    private AgentService agents;
    @Autowired
    private HumanUserService humans;
    @Autowired
    private SettingsService settings;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private TicketCreateTool createTool;
    @Autowired
    private TicketAssignTool assignTool;
    @Autowired
    private TaskApproveTool approveTool;
    @Autowired
    private TicketService tickets;
    @Autowired
    private TaskListService lists;
    @Autowired
    private AgentRuntimeManager runtimes;
    @Autowired
    private ModuleDeps deps;
    @Autowired
    private NaturalTime time;

    private FakeLlmServer server;
    private String roomId;
    private AgentProfile yuzu;
    private AgentProfile kumquat;
    private AgentProfile lime;
    private UserView alice;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeLlmServer();
        server.defaultFor(schema("main"), completion(
                "{\"thought\":\"I recorded the notice\",\"mode\":\"END\",\"actions\":[],\"nextThought\":null}",
                1800, 1024, 20));
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-5522");
        roomId = newRoom();
        yuzu = agents.createNamed(roomId,
                new CreateAgentRequest(Role.PROJECT_MANAGER, null, null, null, null, null), "Yuzu");
        kumquat = agents.createNamed(roomId,
                new CreateAgentRequest(Role.ENGINEER, null, null, null, null, null), "Kumquat");
        lime = agents.createNamed(roomId,
                new CreateAgentRequest(Role.RESEARCHER, null, null, null, List.of(Permission.CHAT_POST), null),
                "Lime");
        alice = humans.join(roomId, "Alice");
    }

    @AfterEach
    void tearDown() {
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.22 🍊 ticket_create records the human requester and assignment selected by exact room-member names. */
    @Test
    void createsAssignedTicketForNamedCoworkerAndRequester() throws Exception {
        ToolResult result = createTool.execute(toolContext(yuzu), new TicketCreateTool.Args(
                "Build the launch page", "Create and verify the responsive landing page.", "@Kumquat", "Alice"));

        assertThat(result.status()).isEqualTo(ToolResult.Status.OK);
        Ticket ticket = tickets.list(roomId).getFirst();
        assertThat(ticket.title()).isEqualTo("Build the launch page");
        assertThat(ticket.assigneeId()).isEqualTo(kumquat.agentId().value());
        assertThat(ticket.requesterName()).isEqualTo("Alice");
        assertThat(result.output()).contains(ticket.id()).contains("Kumquat").contains("notified");
        awaitRequestContaining("assigned ticket " + ticket.id());
    }

    /** v0.0.22 🍊 ticket_assign changes an open ticket and its code-made notice enters the assignee's intake. */
    @Test
    void assignsOpenTicketAndDeliversNoticeThroughIntake() throws Exception {
        Ticket open = tickets.create(roomId, Actor.of(yuzu),
                NewTicket.of("Research competitors", "Compare three citrus beverage launches.")
                        .requestedBy(Actor.of(alice)));

        ToolResult result = assignTool.execute(toolContext(yuzu),
                new TicketAssignTool.Args(open.id(), "Kumquat"));

        Ticket assigned = tickets.get(open.id());
        assertThat(assigned.status()).isEqualTo(TicketStatus.ASSIGNED);
        assertThat(assigned.assigneeId()).isEqualTo(kumquat.agentId().value());
        assertThat(result.output()).contains(open.id()).contains("Kumquat").contains("notified");
        awaitRequestContaining("assigned ticket " + open.id());
    }

    /** v0.0.22 🍊 task_approve archives a finished list published by the caller and advances its linked ticket. */
    @Test
    void approvesCoworkerTaskListAndLinkedTicket() throws Exception {
        Ticket ticket = tickets.create(roomId, Actor.of(yuzu),
                NewTicket.of("Ship prototype", "Build and test the prototype.").assignedTo(kumquat.agentId()));
        TaskList list = lists.create(kumquat.agentId(), "Ship the prototype", Actor.of(yuzu), ticket.id(),
                List.of("Build it", "Test it"));
        lists.apply(kumquat.agentId(), List.of(
                TaskOp.check(list.items().get(0).id(), "built"),
                TaskOp.check(list.items().get(1).id(), "tested")));
        lists.requestApproval(kumquat.agentId());

        ToolResult result = approveTool.execute(toolContext(yuzu), new TaskApproveTool.Args("Kumquat"));

        assertThat(result.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(lists.current(kumquat.agentId()).current()).isNull();
        assertThat(lists.current(kumquat.agentId()).recentArchived()).first()
                .extracting(TaskList::id).isEqualTo(list.id());
        assertThat(tickets.get(ticket.id()).status()).isEqualTo(TicketStatus.APPROVED);
        awaitRequestContaining("approved my task list");
    }

    /** v0.0.22 🍊 every task tool re-checks its permission even when invoked outside the dispatcher. */
    @Test
    void directInvocationStillRequiresTaskPermission() {
        assertThatThrownBy(() -> createTool.execute(toolContext(lime),
                new TicketCreateTool.Args("Unauthorized", "Must not be created", null, null)))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(tickets.list(roomId)).isEmpty();
    }

    /** v0.0.22 🍊 Creates one isolated room for this test. */
    private String newRoom() {
        String id = String.format("room-%04x", ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        jdbc.sql("INSERT INTO room (id, name, created_at) VALUES (:id, 'Task tools', UTC_TIMESTAMP(3))")
                .param("id", id).update();
        return id;
    }

    /** v0.0.22 🍊 A direct tool context for one acting coworker. */
    private ToolContext toolContext(AgentProfile profile) {
        AgentContext ctx = runtimes.require(profile.agentId()).context("trace-task-tools", null, time);
        return new ToolContext(ctx, "batch-task-tools",
                "call-" + ThreadLocalRandom.current().nextInt(1_000_000), 0, "test task tool", 0,
                deps.reporter().start(profile.agentId(), "TOOL", "test", ctx.traceId(), null));
    }

    /** v0.0.22 🍊 Waits until an asynchronous code notice appears in an LLM request. */
    private void awaitRequestContaining(String text) throws InterruptedException {
        for (int i = 0; i < 400 && server.requests().stream().noneMatch(body -> body.contains(text)); i++) {
            Thread.sleep(20);
        }
        assertThat(server.requests()).anyMatch(body -> body.contains(text));
    }
}
