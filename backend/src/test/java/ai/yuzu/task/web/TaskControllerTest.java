package ai.yuzu.task.web;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.Role;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.task.Actor;
import ai.yuzu.task.TaskFixtures;
import ai.yuzu.task.list.TaskList;
import ai.yuzu.task.list.TaskListService;
import ai.yuzu.task.list.TaskOp;
import ai.yuzu.task.ticket.NewTicket;
import ai.yuzu.task.ticket.Ticket;
import ai.yuzu.task.ticket.TicketService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v0.0.20 🍊 REST shapes of tickets, task lists and human approval (field names exactly as in types.ts). */
@IntegrationTest
@AutoConfigureMockMvc
class TaskControllerTest {

    private static final Set<String> TICKET_FIELDS = Set.of("id", "roomId", "title", "detail", "status",
            "creatorName", "assigneeId", "requesterName", "listId", "time", "updatedTime");
    private static final Set<String> TICKET_REQUIRED = Set.of("id", "roomId", "title", "detail", "status",
            "creatorName", "time", "updatedTime");
    private static final Set<String> VIEW_FIELDS = Set.of("agentId", "current", "recentArchived");
    private static final Set<String> LIST_FIELDS = Set.of("id", "agentId", "goal", "status", "publisherId",
            "publisherName", "ticketId", "outcome", "items", "time", "archivedTime");
    private static final Set<String> LIST_REQUIRED = Set.of("id", "agentId", "goal", "status", "publisherId",
            "publisherName", "items", "time");
    private static final Set<String> ITEM_FIELDS = Set.of("id", "ord", "text", "state", "note", "struckReason");
    private static final Set<String> ITEM_REQUIRED = Set.of("id", "ord", "text", "state");
    private static final String NATURAL_TIME = "^[A-Z][a-z]{2} [A-Z][a-z]{2} \\d{1,2}, \\d{1,2}:\\d{2}:\\d{2} [AP]M$";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private TicketService tickets;

    @Autowired
    private TaskListService taskLists;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AgentService agents;

    @Autowired
    private HumanUserService humans;

    private TaskFixtures fixtures;
    private String room;
    private AgentProfile pm;
    private AgentProfile lime;
    private Actor alice;

    /** v0.0.20 🍊 A fresh room with a project manager, a researcher and Alice. */
    @BeforeEach
    void setUp() {
        fixtures = new TaskFixtures(jdbc, agents, humans);
        room = fixtures.newRoom();
        pm = fixtures.hire(room, Role.PROJECT_MANAGER);
        lime = fixtures.hire(room, Role.RESEARCHER);
        alice = fixtures.human(room, "Alice");
    }

    /** v0.0.20 🍊 GET /api/rooms/{roomId}/tickets returns Ticket[] with exactly the contract fields. */
    @Test
    void ticketBoardShape() throws Exception {
        Ticket ticket = tickets.create(room, Actor.of(pm), NewTicket.of("Board", "Shape check")
                .assignedTo(lime.agentId()).requestedBy(alice));
        JsonNode board = call(get("/api/rooms/" + room + "/tickets"), status().isOk());
        assertThat(board.isArray()).isTrue();
        assertThat(board).hasSize(1);
        JsonNode json = board.get(0);
        assertShape(json, TICKET_FIELDS, TICKET_REQUIRED);
        assertThat(json.get("id").asText()).isEqualTo(ticket.id());
        assertThat(json.get("status").asText()).isEqualTo("ASSIGNED");
        assertThat(json.get("assigneeId").asText()).isEqualTo(lime.agentId().value());
        assertThat(json.get("requesterName").asText()).isEqualTo("Alice");
        assertThat(json.get("time").asText()).matches(NATURAL_TIME);
        assertThat(json.get("updatedTime").asText()).matches(NATURAL_TIME);

        assertError(call(get("/api/rooms/room-0000/tickets"), status().isNotFound()), "NOT_FOUND");
    }

    /** v0.0.20 🍊 GET /api/agents/{agentId}/tasks returns TaskListView with exactly the contract fields. */
    @Test
    void taskListShape() throws Exception {
        JsonNode empty = call(get("/api/agents/" + lime.agentId() + "/tasks"), status().isOk());
        assertThat(fieldNames(empty)).containsExactlyInAnyOrderElementsOf(VIEW_FIELDS);
        assertThat(empty.get("current").isNull()).isTrue();
        assertThat(empty.get("recentArchived").isArray()).isTrue();

        TaskList list = taskLists.create(lime.agentId(), "Research", Actor.of(pm), null, List.of("Collect", "Compare"));
        taskLists.apply(lime.agentId(), List.of(TaskOp.check(list.itemAt(1).orElseThrow().id(), "12 sources"),
                TaskOp.strike(list.itemAt(2).orElseThrow().id(), "Out of scope"),
                TaskOp.editGoal("Research prices", "Narrower scope")));
        JsonNode view = call(get("/api/agents/" + lime.agentId() + "/tasks"), status().isOk());
        assertThat(fieldNames(view)).containsExactlyInAnyOrderElementsOf(VIEW_FIELDS);
        JsonNode current = view.get("current");
        assertShape(current, LIST_FIELDS, LIST_REQUIRED);
        assertThat(current.get("goal").asText()).isEqualTo("Research prices");
        assertThat(current.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(current.get("publisherId").asText()).isEqualTo(pm.agentId().value());
        assertThat(current.get("publisherName").asText()).isEqualTo(pm.name());
        assertThat(current.get("time").asText()).matches(NATURAL_TIME);
        JsonNode items = current.get("items");
        assertThat(items).hasSize(2);
        items.forEach(item -> assertShape(item, ITEM_FIELDS, ITEM_REQUIRED));
        assertThat(items.get(0).get("ord").asInt()).isEqualTo(1);
        assertThat(items.get(0).get("state").asText()).isEqualTo("DONE");
        assertThat(items.get(0).get("note").asText()).isEqualTo("12 sources");
        assertThat(items.get(1).get("state").asText()).isEqualTo("STRUCK");
        assertThat(items.get(1).get("struckReason").asText()).isEqualTo("Out of scope");

        assertError(call(get("/api/agents/agent-0000/tasks"), status().isNotFound()), "NOT_FOUND");
        assertError(call(get("/api/agents/not-an-agent/tasks"), status().isBadRequest()), "BAD_REQUEST");
    }

    /** v0.0.20 🍊 POST /api/task-lists/{listId}/approve by a human archives the list and returns the new view. */
    @Test
    void humanApprovalArchives() throws Exception {
        TaskList list = taskLists.create(lime.agentId(), "Summarize feedback", Actor.of(pm), null, List.of("Read"));
        taskLists.apply(lime.agentId(), List.of(TaskOp.check(list.itemAt(1).orElseThrow().id(), null)));
        taskLists.requestApproval(lime.agentId());

        JsonNode view = call(approve(list.id(), alice.id()), status().isOk());
        assertThat(fieldNames(view)).containsExactlyInAnyOrderElementsOf(VIEW_FIELDS);
        assertThat(view.get("current").isNull()).isTrue();
        JsonNode archived = view.get("recentArchived").get(0);
        assertShape(archived, LIST_FIELDS, LIST_REQUIRED);
        assertThat(archived.get("id").asText()).isEqualTo(list.id());
        assertThat(archived.get("status").asText()).isEqualTo("ARCHIVED");
        assertThat(archived.get("archivedTime").asText()).matches(NATURAL_TIME);
        assertThat(archived.get("outcome").asText()).isEqualTo("Completed: 1 item done. Approved by Alice (human).");
    }

    /** v0.0.20 🍊 Approval errors: unfinished list, unknown user or list, missing body field, other room's human. */
    @Test
    void approvalErrors() throws Exception {
        TaskList list = taskLists.create(lime.agentId(), "Still working", Actor.of(pm), null, List.of("Read"));
        assertError(call(approve(list.id(), alice.id()), status().isConflict()), "CONFLICT");
        assertError(call(approve(list.id(), "user-0000"), status().isNotFound()), "NOT_FOUND");
        assertError(call(approve("list-0000-0000000000", alice.id()), status().isNotFound()), "NOT_FOUND");
        assertError(call(post("/api/task-lists/" + list.id() + "/approve").contentType(MediaType.APPLICATION_JSON)
                .content("{}"), status().isBadRequest()), "BAD_REQUEST");

        taskLists.apply(lime.agentId(), List.of(TaskOp.check(list.itemAt(1).orElseThrow().id(), null)));
        taskLists.requestApproval(lime.agentId());
        Actor stranger = fixtures.human(fixtures.newRoom(), "Stranger");
        assertError(call(approve(list.id(), stranger.id()), status().isForbidden()), "PERMISSION_DENIED");
        assertThat(taskLists.current(lime.agentId()).current().status().name()).isEqualTo("AWAITING_APPROVAL");
    }

    /** v0.0.20 🍊 /api/bootstrap carries the room's tickets and one TaskListView per present agent. */
    @Test
    void bootstrapCarriesTicketsAndTaskLists() throws Exception {
        Ticket ticket = tickets.create(room, alice, NewTicket.of("Bootstrap", "Shown on the board"));
        taskLists.create(lime.agentId(), "Visible", alice, null, List.of("A"));

        JsonNode snapshot = call(get("/api/bootstrap").param("roomId", room), status().isOk());
        assertThat(snapshot.get("tickets")).hasSize(1);
        assertThat(snapshot.get("tickets").get(0).get("id").asText()).isEqualTo(ticket.id());
        JsonNode lists = snapshot.get("taskLists");
        assertThat(lists).hasSize(2);
        List<String> agentIds = new ArrayList<>();
        lists.forEach(view -> {
            assertThat(fieldNames(view)).containsExactlyInAnyOrderElementsOf(VIEW_FIELDS);
            agentIds.add(view.get("agentId").asText());
        });
        assertThat(agentIds).containsExactlyInAnyOrder(pm.agentId().value(), lime.agentId().value());
        lists.forEach(view -> {
            if (view.get("agentId").asText().equals(lime.agentId().value())) {
                assertThat(view.get("current").get("goal").asText()).isEqualTo("Visible");
            } else {
                assertThat(view.get("current").isNull()).isTrue();
            }
        });
    }

    /** v0.0.20 🍊 The POST request of a human approval. */
    private static RequestBuilder approve(String listId, String userId) {
        return post("/api/task-lists/" + listId + "/approve").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + userId + "\"}");
    }

    /** v0.0.20 🍊 Performs a request, checks the status and parses the JSON body. */
    private JsonNode call(RequestBuilder request, ResultMatcher expected)
            throws Exception {
        return mapper.readTree(mvc.perform(request).andExpect(expected).andReturn().getResponse().getContentAsString());
    }

    /** v0.0.20 🍊 Only contract fields are present, and every required one is. */
    private static void assertShape(JsonNode json, Set<String> allowed, Set<String> required) {
        assertThat(allowed).containsAll(fieldNames(json));
        assertThat(fieldNames(json)).containsAll(required);
    }

    /** v0.0.20 🍊 An ApiError body with the expected code. */
    private static void assertError(JsonNode json, String code) {
        assertThat(json.get("code").asText()).isEqualTo(code);
        assertThat(json.get("message").asText()).isNotBlank();
    }

    /** v0.0.20 🍊 Field names of a JSON object. */
    private static List<String> fieldNames(JsonNode json) {
        List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
