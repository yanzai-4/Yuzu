package ai.yuzu.task.ticket;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.Role;
import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.ConflictException;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.task.Actor;
import ai.yuzu.task.TaskFixtures;
import ai.yuzu.task.list.TaskList;
import ai.yuzu.task.list.TaskListService;
import ai.yuzu.task.list.TaskOp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.20 🍊 Tickets against MySQL: who may create and assign, the lifecycle, list links and ticket.upsert events. */
@IntegrationTest
@AutoConfigureMockMvc
class TicketServiceTest {

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

    @Autowired
    private SseHub hub;

    @Autowired
    private ObjectMapper mapper;

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

    /** v0.0.20 🍊 Humans and TASK_ASSIGN agents create tickets; ids carry the owner (system for humans). */
    @Test
    void humansAndAssignersCreateTickets() {
        Ticket byHuman = tickets.create(room, alice,
                NewTicket.of("  Landing page ", "Build a landing page\r\nwith pricing"));
        assertThat(byHuman.id()).matches("^ticket-0000-[0-9a-f]{10}$");
        assertThat(byHuman.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(byHuman.title()).isEqualTo("Landing page");
        assertThat(byHuman.detail()).isEqualTo("Build a landing page\nwith pricing");
        assertThat(byHuman.creatorName()).isEqualTo("Alice");
        assertThat(byHuman.requesterName()).isEqualTo("Alice");
        assertThat(byHuman.assigneeId()).isNull();

        Ticket byPm = tickets.create(room, Actor.of(pm), NewTicket.of("Research", "Compare citrus prices")
                .assignedTo(lime.agentId()).requestedBy(alice));
        assertThat(byPm.id()).startsWith("ticket-" + pm.agentId().hex() + "-");
        assertThat(byPm.status()).isEqualTo(TicketStatus.ASSIGNED);
        assertThat(byPm.assigneeId()).isEqualTo(lime.agentId().value());
        assertThat(byPm.creatorName()).isEqualTo(pm.name());
        assertThat(byPm.requesterName()).isEqualTo("Alice");
        assertThat(byPm.assignedBy()).isEqualTo(Actor.of(pm));

        assertThat(tickets.list(room)).extracting(Ticket::id).containsExactly(byHuman.id(), byPm.id());
        assertThat(tickets.get(byPm.id())).isEqualTo(byPm);
        assertThatThrownBy(() -> tickets.create(room, alice, NewTicket.of(" ", "")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> tickets.list("room-0000")).isInstanceOf(NotFoundException.class);
    }

    /** v0.0.20 🍊 Agents without TASK_ASSIGN can neither create, assign nor cancel tickets. */
    @Test
    void agentsWithoutTaskAssignCannotCreateOrAssign() {
        assertThatThrownBy(() -> tickets.create(room, Actor.of(lime), NewTicket.of("Mine", "")))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("TASK_ASSIGN");
        Ticket open = tickets.create(room, alice, NewTicket.of("Open work", ""));
        assertThatThrownBy(() -> tickets.assign(open.id(), lime.agentId(), Actor.of(lime)))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("TASK_ASSIGN");
        assertThatThrownBy(() -> tickets.updateStatus(open.id(), TicketStatus.CANCELLED, Actor.of(lime)))
                .isInstanceOf(PermissionDeniedException.class);
        AgentProfile outsider = fixtures.hire(fixtures.newRoom(), Role.PROJECT_MANAGER);
        assertThatThrownBy(() -> tickets.assign(open.id(), lime.agentId(), Actor.of(outsider)))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("not a member");
        assertThat(tickets.get(open.id()).status()).isEqualTo(TicketStatus.OPEN);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM ticket WHERE room_id = :room").param("room", room)
                .query(Long.class).single()).isEqualTo(1L);
    }

    /** v0.0.20 🍊 The lifecycle is enforced, with per-status permissions. */
    @Test
    void statusTransitionsAreChecked() {
        Ticket ticket = tickets.create(room, alice, NewTicket.of("Copy", "Write the landing copy"));
        assertThatThrownBy(() -> tickets.updateStatus(ticket.id(), TicketStatus.IN_PROGRESS, alice))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> tickets.updateStatus(ticket.id(), TicketStatus.ASSIGNED, alice))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Use assign");

        Ticket assigned = tickets.assign(ticket.id(), lime.agentId(), Actor.of(pm));
        assertThat(assigned.status()).isEqualTo(TicketStatus.ASSIGNED);
        assertThat(assigned.assignedBy()).isEqualTo(Actor.of(pm));
        assertThat(tickets.updateStatus(ticket.id(), TicketStatus.IN_PROGRESS, Actor.of(lime)).status())
                .isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(tickets.updateStatus(ticket.id(), TicketStatus.DONE, Actor.of(lime)).status())
                .isEqualTo(TicketStatus.DONE);
        assertThatThrownBy(() -> tickets.updateStatus(ticket.id(), TicketStatus.APPROVED, Actor.of(lime)))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(tickets.updateStatus(ticket.id(), TicketStatus.APPROVED, Actor.of(pm)).status())
                .isEqualTo(TicketStatus.APPROVED);
        assertThatThrownBy(() -> tickets.updateStatus(ticket.id(), TicketStatus.CANCELLED, alice))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> tickets.assign(ticket.id(), pm.agentId(), alice))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> tickets.get("ticket-0000-0000000000")).isInstanceOf(NotFoundException.class);
    }

    /** v0.0.20 🍊 A ticket follows its task list: IN_PROGRESS on start, DONE when finished, APPROVED on approval. */
    @Test
    void ticketFollowsItsTaskList() {
        Ticket ticket = tickets.create(room, Actor.of(pm), NewTicket.of("Prices", "Compare citrus prices")
                .assignedTo(lime.agentId()).requestedBy(alice));
        TaskList list = taskLists.create(lime.agentId(), "Compare citrus prices", null, ticket.id(),
                List.of("Collect"));
        assertThat(list.publisherId()).isEqualTo(pm.agentId().value());
        assertThat(list.ticketId()).isEqualTo(ticket.id());
        Ticket started = tickets.get(ticket.id());
        assertThat(started.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(started.listId()).isEqualTo(list.id());

        taskLists.apply(lime.agentId(), List.of(TaskOp.check(list.itemAt(1).orElseThrow().id(), null)));
        taskLists.requestApproval(lime.agentId());
        assertThat(tickets.get(ticket.id()).status()).isEqualTo(TicketStatus.DONE);
        assertThatThrownBy(() -> tickets.updateStatus(ticket.id(), TicketStatus.APPROVED, alice))
                .isInstanceOf(ConflictException.class).hasMessageContaining("approve that task list instead");

        taskLists.apply(lime.agentId(), List.of(TaskOp.add("Double-check the sources")));
        assertThat(tickets.get(ticket.id()).status()).isEqualTo(TicketStatus.IN_PROGRESS);
        TaskList reopened = taskLists.current(lime.agentId()).current();
        taskLists.apply(lime.agentId(), List.of(TaskOp.check(reopened.itemAt(2).orElseThrow().id(), null)));
        taskLists.requestApproval(lime.agentId());

        taskLists.approve(list.id(), Actor.of(pm));
        assertThat(tickets.get(ticket.id()).status()).isEqualTo(TicketStatus.APPROVED);
    }

    /** v0.0.20 🍊 A task list can only start from a ticket assigned to its owner and not yet linked. */
    @Test
    void listsStartOnlyFromTheOwnersTickets() {
        Ticket pmsOwn = tickets.create(room, Actor.of(pm), NewTicket.of("PM work", "").assignedTo(pm.agentId()));
        assertThatThrownBy(() -> taskLists.create(lime.agentId(), "Not mine", null, pmsOwn.id(), List.of("A")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("is not assigned to");
        Ticket open = tickets.create(room, alice, NewTicket.of("Unassigned", ""));
        assertThatThrownBy(() -> taskLists.create(lime.agentId(), "Too early", null, open.id(), List.of("A")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> taskLists.create(lime.agentId(), "Unknown", null, "ticket-0000-0000000000",
                List.of("A"))).isInstanceOf(NotFoundException.class);
    }

    /** v0.0.20 🍊 linkList attaches an existing list to a ticket and updates both sides. */
    @Test
    void linkListAttachesAnExistingList() {
        TaskList list = taskLists.create(lime.agentId(), "Own initiative", alice, null, List.of("A"));
        Ticket ticket = tickets.create(room, Actor.of(pm), NewTicket.of("Formalize it", "").assignedTo(lime.agentId()));
        Ticket other = tickets.create(room, Actor.of(pm), NewTicket.of("Other work", "").assignedTo(lime.agentId()));
        assertThatThrownBy(() -> tickets.linkList(ticket.id(), "list-" + pm.agentId().hex() + "-0000000000",
                Actor.of(lime))).isInstanceOf(BadRequestException.class);
        AgentProfile kumquat = fixtures.hire(room, Role.ENGINEER);
        assertThatThrownBy(() -> tickets.linkList(ticket.id(), list.id(), Actor.of(kumquat)))
                .isInstanceOf(PermissionDeniedException.class);

        Ticket linked = tickets.linkList(ticket.id(), list.id(), Actor.of(lime));
        assertThat(linked.listId()).isEqualTo(list.id());
        assertThat(linked.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(taskLists.current(lime.agentId()).current().ticketId()).isEqualTo(ticket.id());
        assertThatThrownBy(() -> tickets.linkList(other.id(), list.id(), Actor.of(lime)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already works on ticket");
    }

    /** v0.0.20 🍊 Every ticket change publishes ticket.upsert with the contract shape. */
    @Test
    void publishesTicketUpserts() {
        long before = hub.currentCursor();
        Ticket ticket = tickets.create(room, alice, NewTicket.of("Events", "Watch the board"));
        tickets.assign(ticket.id(), lime.agentId(), alice);
        tickets.assign(ticket.id(), lime.agentId(), alice);

        List<JsonNode> events = hub.bufferedEvents().stream()
                .filter(event -> event.id() > before && event.type() == EventType.TICKET_UPSERT)
                .map(event -> json(event.json()))
                .filter(node -> ticket.id().equals(node.path("data").path("id").asText()))
                .toList();
        assertThat(events).extracting(node -> node.path("data").path("status").asText())
                .containsExactly("OPEN", "ASSIGNED");
        assertThat(events.get(1).path("type").asText()).isEqualTo("ticket.upsert");
        assertThat(events.get(1).path("roomId").asText()).isEqualTo(room);
        assertThat(events.get(1).path("agentId").asText()).isEqualTo(lime.agentId().value());
        assertThat(events.get(1).path("data").has("creator")).isFalse();
        assertThat(events.get(1).path("data").has("assignedBy")).isFalse();
    }

    /** v0.0.20 🍊 Parses an event envelope. */
    private JsonNode json(String body) {
        try {
            return mapper.readTree(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
