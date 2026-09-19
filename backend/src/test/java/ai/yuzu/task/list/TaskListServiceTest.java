package ai.yuzu.task.list;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.Permission;
import ai.yuzu.agent.Role;
import ai.yuzu.common.error.BadRequestException;
import ai.yuzu.common.error.ConflictException;
import ai.yuzu.common.error.NotFoundException;
import ai.yuzu.common.error.PermissionDeniedException;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.common.id.DataName;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.realtime.EventType;
import ai.yuzu.realtime.SseHub;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.task.Actor;
import ai.yuzu.task.TaskFixtures;
import ai.yuzu.task.prompt.TaskPromptRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** v0.0.20 🍊 Task lists against MySQL: op rules, one current list, approval and archiving, events, concurrency. */
@IntegrationTest
@AutoConfigureMockMvc
class TaskListServiceTest {

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

    /** v0.0.20 🍊 A fresh room with a project manager (the publisher), a researcher (the list owner) and Alice. */
    @BeforeEach
    void setUp() {
        fixtures = new TaskFixtures(jdbc, agents, humans);
        room = fixtures.newRoom();
        pm = fixtures.hire(room, Role.PROJECT_MANAGER);
        lime = fixtures.hire(room, Role.RESEARCHER);
        alice = fixtures.human(room, "Alice");
    }

    /** v0.0.20 🍊 Checking, striking and noting change items in place; struck items stay with their reason. */
    @Test
    void checkingAndStrikingKeepEveryItem() {
        TaskList list = taskLists.create(lime.agentId(), "Research citrus prices", Actor.of(pm), null,
                List.of("Collect prices", "Compare regions", "Write the summary"));
        assertThat(list.status()).isEqualTo(TaskListStatus.ACTIVE);
        assertThat(list.id()).startsWith("list-" + lime.agentId().hex() + "-");
        assertThat(list.items()).extracting(TaskItem::id).allMatch(id -> id.startsWith("item-" + lime.agentId().hex()));
        String collect = itemId(list, 1);
        String compare = itemId(list, 2);
        String write = itemId(list, 3);

        TaskList after = taskLists.apply(lime.agentId(), List.of(TaskOp.start(collect),
                TaskOp.check(collect, "12 sources"), TaskOp.strike(compare, "Only one region matters"),
                TaskOp.start(write)));
        assertThat(after.items()).hasSize(3);
        assertThat(after.itemAt(1).orElseThrow().state()).isEqualTo(TaskItemState.DONE);
        assertThat(after.itemAt(1).orElseThrow().note()).isEqualTo("12 sources");
        TaskItem struck = after.itemAt(2).orElseThrow();
        assertThat(struck.state()).isEqualTo(TaskItemState.STRUCK);
        assertThat(struck.struckReason()).isEqualTo("Only one region matters");
        assertThat(struck.text()).isEqualTo("Compare regions");
        assertThat(after.itemAt(3).orElseThrow().state()).isEqualTo(TaskItemState.DOING);

        int version = version(list.id());
        TaskList again = taskLists.apply(lime.agentId(), List.of(TaskOp.check(collect, "12 sources")));
        assertThat(again).isEqualTo(after);
        assertThat(version(list.id())).isEqualTo(version);

        TaskList struckDone = taskLists.apply(lime.agentId(), List.of(TaskOp.strike(collect, "The data was stale")));
        assertThat(struckDone.itemAt(1).orElseThrow().state()).isEqualTo(TaskItemState.STRUCK);
        assertThat(struckDone.itemAt(1).orElseThrow().note()).isEqualTo("12 sources");
        assertThat(itemRows(list.id())).isEqualTo(3);
    }

    /** v0.0.20 🍊 One invalid operation rejects the whole batch with a typed error listing every problem. */
    @Test
    void invalidOperationsRejectTheWholeBatch() {
        TaskList list = taskLists.create(lime.agentId(), "Plan the launch", alice, null, List.of("One", "Two"));
        String one = itemId(list, 1);
        String two = itemId(list, 2);
        taskLists.apply(lime.agentId(), List.of(TaskOp.check(one, null), TaskOp.strike(two, "Not needed")));

        assertThatThrownBy(() -> taskLists.apply(lime.agentId(),
                List.of(TaskOp.add("Three"), TaskOp.check("item-ffff-0000000000", null))))
                .isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat((List<?>) ((YuzuException) e).details().get("problems")).hasSize(1));
        assertThatThrownBy(() -> taskLists.apply(lime.agentId(), List.of(TaskOp.start(one))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> taskLists.apply(lime.agentId(), List.of(TaskOp.check(two, null))))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> taskLists.apply(lime.agentId(), List.of(TaskOp.strike(one, " "))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> taskLists.apply(lime.agentId(), List.of()))
                .isInstanceOf(BadRequestException.class);
        assertThat(taskLists.current(lime.agentId()).current().items()).hasSize(2);
        assertThat(itemRows(list.id())).isEqualTo(2);

        assertThat(taskLists.validate(lime.agentId(), List.of(TaskOp.start(one), TaskOp.add("Three"))))
                .singleElement().asString().startsWith("Operation 1 (START " + one + ") was rejected");
        assertThat(taskLists.validate(lime.agentId(), List.of(TaskOp.add("Three")))).isEmpty();
    }

    /** v0.0.20 🍊 An agent has at most one current list (service and database), and no ops without one. */
    @Test
    void anAgentHasOneCurrentList() {
        assertThatThrownBy(() -> taskLists.apply(lime.agentId(), List.of(TaskOp.add("Early"))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("no current task list");
        assertThatThrownBy(() -> taskLists.requestApproval(lime.agentId())).isInstanceOf(ConflictException.class);

        TaskList first = taskLists.create(lime.agentId(), "First", alice, null, List.of("A"));
        assertThatThrownBy(() -> taskLists.create(lime.agentId(), "Second", alice, null, List.of("B")))
                .isInstanceOf(ConflictException.class);
        taskLists.apply(lime.agentId(), List.of(TaskOp.check(itemId(first, 1), null)));
        taskLists.requestApproval(lime.agentId());
        assertThatThrownBy(() -> taskLists.create(lime.agentId(), "Second", alice, null, List.of("B")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> taskLists.apply(lime.agentId(), "list-ffff-0000000000", List.of(TaskOp.add("C"))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("is not the current task list");

        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO task_list (agent_id, id, goal, status, publisher_kind, publisher_id, publisher_name,
                            created_at, updated_at)
                        VALUES (:agentId, :id, 'Sneaky', 'ACTIVE', 'HUMAN', :user, 'Alice', UTC_TIMESTAMP(3),
                            UTC_TIMESTAMP(3))
                        """)
                .param("agentId", lime.agentId().value())
                .param("id", IdGen.recordId(DataName.TASK_LIST, lime.agentId()))
                .param("user", alice.id())
                .update()).isInstanceOf(DuplicateKeyException.class).hasMessageContaining("uk_open_list");
    }

    /** v0.0.20 🍊 A list archives only when complete, awaiting approval, and approved by its publisher. */
    @Test
    void archivesOnlyAfterCompletionAndPublisherApproval() {
        AgentProfile auditor = fixtures.hire(room, Role.RESEARCHER, Permission.CHAT_POST, Permission.TASK_APPROVE);
        TaskList list = taskLists.create(lime.agentId(), "Research", Actor.of(pm), null,
                List.of("Collect", "Compare"));
        assertThatThrownBy(() -> taskLists.requestApproval(lime.agentId()))
                .isInstanceOf(ConflictException.class).hasMessageStartingWith("Items 1, 2 are not finished");
        assertThatThrownBy(() -> taskLists.approve(list.id(), Actor.of(pm)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("still ACTIVE");

        taskLists.apply(lime.agentId(), List.of(TaskOp.check(itemId(list, 1), null),
                TaskOp.strike(itemId(list, 2), "Out of scope")));
        TaskList awaiting = taskLists.requestApproval(lime.agentId());
        assertThat(awaiting.status()).isEqualTo(TaskListStatus.AWAITING_APPROVAL);
        assertThat(taskLists.requestApproval(lime.agentId())).isEqualTo(awaiting);

        assertThatThrownBy(() -> taskLists.approve(list.id(), Actor.of(lime)))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("its own task list");
        assertThatThrownBy(() -> taskLists.approve(list.id(), Actor.of(auditor)))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("Only the publisher");
        assertThat(taskLists.current(lime.agentId()).current().status()).isEqualTo(TaskListStatus.AWAITING_APPROVAL);

        TaskListView view = taskLists.approve(list.id(), Actor.of(pm));
        assertThat(view.current()).isNull();
        TaskList archived = view.recentArchived().getFirst();
        assertThat(archived.id()).isEqualTo(list.id());
        assertThat(archived.status()).isEqualTo(TaskListStatus.ARCHIVED);
        assertThat(archived.archivedTime()).isNotBlank();
        assertThat(archived.outcome()).isEqualTo("Completed with changes: 1 of 2 items done, 1 struck. Approved by "
                + pm.name() + " (agent).");
        assertThat(archived.items()).extracting(TaskItem::state)
                .containsExactly(TaskItemState.DONE, TaskItemState.STRUCK);
        assertThat(taskLists.current(lime.agentId())).isEqualTo(view);

        assertThatThrownBy(() -> taskLists.approve(list.id(), Actor.of(pm)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("already archived");
        assertThatThrownBy(() -> taskLists.apply(lime.agentId(), list.id(), List.of(TaskOp.add("Late"))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("archived lists never change");
        assertThat(taskLists.create(lime.agentId(), "Next", alice, null, List.of("X")).status())
                .isEqualTo(TaskListStatus.ACTIVE);
    }

    /** v0.0.20 🍊 Any human of the room can approve; humans of other rooms and unknown lists cannot. */
    @Test
    void humansAreTheUltimateApprovers() {
        TaskList list = finishedList("Summarize the feedback", Actor.of(pm));
        Actor stranger = fixtures.human(fixtures.newRoom(), "Stranger");
        assertThatThrownBy(() -> taskLists.approve(list.id(), stranger)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> taskLists.approve("list-0000-0000000000", alice))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> taskLists.approve("nonsense", alice)).isInstanceOf(NotFoundException.class);

        Actor bob = fixtures.human(room, "Bob");
        TaskListView view = taskLists.approve(list.id(), bob);
        assertThat(view.recentArchived().getFirst().outcome())
                .isEqualTo("Completed: 1 item done. Approved by Bob (human).");
    }

    /** v0.0.20 🍊 Publishers need TASK_ASSIGN; an agent publisher also needs TASK_APPROVE to approve. */
    @Test
    void publishersNeedAssignAndApproveRights() {
        AgentProfile kumquat = fixtures.hire(room, Role.ENGINEER);
        assertThatThrownBy(() -> taskLists.create(lime.agentId(), "Goal", Actor.of(kumquat), null, List.of("A")))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("TASK_ASSIGN");
        assertThatThrownBy(() -> taskLists.create(lime.agentId(), "Goal", null, null, List.of("A")))
                .isInstanceOf(BadRequestException.class);

        AgentProfile lead = fixtures.hire(room, Role.RESEARCHER, Permission.CHAT_POST, Permission.TASK_ASSIGN);
        TaskList list = finishedList("Lead's request", Actor.of(lead));
        assertThatThrownBy(() -> taskLists.approve(list.id(), Actor.of(lead)))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("TASK_APPROVE");
        assertThat(taskLists.approve(list.id(), alice).current()).isNull();
    }

    /** v0.0.20 🍊 New work added while awaiting approval sends the list back to ACTIVE. */
    @Test
    void newWorkReopensAnAwaitingList() {
        TaskList list = finishedList("Write the FAQ", alice);
        TaskList reopened = taskLists.apply(lime.agentId(), List.of(TaskOp.add("Also answer the pricing question")));
        assertThat(reopened.status()).isEqualTo(TaskListStatus.ACTIVE);
        assertThat(reopened.items()).hasSize(2);
        assertThatThrownBy(() -> taskLists.approve(list.id(), alice)).isInstanceOf(ConflictException.class);
    }

    /** v0.0.20 🍊 Editing the goal keeps the old goal (stored, shown to prompts). */
    @Test
    void editingTheGoalKeepsTheOldOne() {
        TaskList list = taskLists.create(lime.agentId(), "Research lemons", alice, null, List.of("Collect"));
        TaskList edited = taskLists.apply(lime.agentId(),
                List.of(TaskOp.editGoal("Research limes", "Alice changed the fruit")));
        assertThat(edited.goal()).isEqualTo("Research limes");
        assertThat(edited.previousGoals()).singleElement().satisfies(previous -> {
            assertThat(previous.goal()).isEqualTo("Research lemons");
            assertThat(previous.reason()).isEqualTo("Alice changed the fruit");
        });
        assertThat(TaskPromptRenderer.renderCurrent(taskLists.current(lime.agentId())))
                .contains("Goal: Research limes\nEarlier goal: Research lemons (replaced ")
                .contains("; reason: Alice changed the fruit)");
        String stored = jdbc.sql("SELECT goal_history FROM task_list WHERE agent_id = :agentId AND id = :id")
                .param("agentId", lime.agentId().value()).param("id", list.id()).query(String.class).single();
        assertThat(stored).contains("Research lemons").contains("Alice changed the fruit");
    }

    /** v0.0.20 🍊 Planning sees the last three archived lists, most recent first, with their outcomes. */
    @Test
    void recentArchivedReturnsTheLastThree() {
        for (int i = 1; i <= 4; i++) {
            taskLists.approve(finishedList("Goal " + i, alice).id(), alice);
        }
        List<TaskList> recent = taskLists.recentArchived(lime.agentId(), 3);
        assertThat(recent).extracting(TaskList::goal).containsExactly("Goal 4", "Goal 3", "Goal 2");
        assertThat(taskLists.recentArchived(lime.agentId(), 10)).extracting(TaskList::goal)
                .containsExactly("Goal 4", "Goal 3", "Goal 2", "Goal 1");
        assertThat(taskLists.current(lime.agentId()).recentArchived()).isEqualTo(recent);
        String history = TaskPromptRenderer.renderHistory(recent);
        assertThat(history).startsWith("Recent archived task lists (most recent first):\n1. Goal: Goal 4\n");
        assertThat(history).contains("   Outcome: Completed: 1 item done. Approved by Alice (human).");
    }

    /** v0.0.20 🍊 Every change publishes task.list with the agent's full view. */
    @Test
    void everyChangeIsPublished() {
        long before = hub.currentCursor();
        TaskList list = taskLists.create(lime.agentId(), "Publish", alice, null, List.of("A"));
        taskLists.apply(lime.agentId(), List.of(TaskOp.start(itemId(list, 1))));
        taskLists.apply(lime.agentId(), List.of(TaskOp.start(itemId(list, 1))));

        List<JsonNode> events = hub.bufferedEvents().stream()
                .filter(event -> event.id() > before && event.type() == EventType.TASK_LIST)
                .map(event -> json(event.json()))
                .filter(node -> lime.agentId().value().equals(node.path("agentId").asText()))
                .toList();
        assertThat(events).hasSize(2);
        JsonNode last = events.get(1);
        assertThat(last.path("type").asText()).isEqualTo("task.list");
        assertThat(last.path("roomId").asText()).isEqualTo(room);
        assertThat(last.path("data").path("agentId").asText()).isEqualTo(lime.agentId().value());
        assertThat(last.path("data").path("current").path("items").get(0).path("state").asText()).isEqualTo("DOING");
        assertThat(last.path("data").path("recentArchived").isArray()).isTrue();
        assertThat(last.path("data").path("current").has("publisherKind")).isFalse();
        assertThat(last.path("data").path("current").has("previousGoals")).isFalse();
    }

    /** v0.0.20 🍊 Eight threads applying operations at once lose nothing (per-agent lock + optimistic version). */
    @Test
    void eightConcurrentPlannersLoseNothing() throws Exception {
        TaskList list = taskLists.create(lime.agentId(), "Concurrency", alice, null, List.of());
        int threads = 8;
        int perThread = 6;
        assertThat(threads * perThread).isLessThanOrEqualTo(TaskListDraft.MAX_ITEMS);
        runConcurrently(threads, thread -> {
            for (int i = 0; i < perThread; i++) {
                taskLists.apply(lime.agentId(), List.of(TaskOp.add("t" + thread + "-" + i)));
            }
        });
        TaskList added = taskLists.current(lime.agentId()).current();
        assertThat(added.items()).hasSize(threads * perThread);
        assertThat(added.items()).extracting(TaskItem::ord)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, threads * perThread).boxed().toList());
        List<String> expected = IntStream.range(0, threads).boxed()
                .flatMap(t -> IntStream.range(0, perThread).mapToObj(i -> "t" + t + "-" + i)).toList();
        assertThat(added.items()).extracting(TaskItem::text).containsExactlyInAnyOrderElementsOf(expected);
        for (int t = 0; t < threads; t++) {
            String prefix = "t" + t + "-";
            assertThat(added.items().stream().filter(item -> item.text().startsWith(prefix)).map(TaskItem::text))
                    .containsExactlyElementsOf(IntStream.range(0, perThread).mapToObj(i -> prefix + i).toList());
        }
        assertThat(version(list.id())).isEqualTo(threads * perThread);

        runConcurrently(threads, thread -> {
            for (int i = 0; i < perThread; i++) {
                String itemId = itemId(added, thread * perThread + i + 1);
                taskLists.apply(lime.agentId(), List.of(TaskOp.start(itemId), TaskOp.check(itemId, "by t" + thread)));
            }
        });
        TaskList done = taskLists.current(lime.agentId()).current();
        assertThat(done.items()).extracting(TaskItem::state).containsOnly(TaskItemState.DONE);
        assertThat(version(list.id())).isEqualTo(2 * threads * perThread);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM task_item WHERE agent_id = :agentId AND state = 'DONE'")
                .param("agentId", lime.agentId().value()).query(Long.class).single())
                .isEqualTo((long) threads * perThread);
    }

    /** v0.0.20 🍊 One job run by each of {@code threads} virtual threads released together; fails on any error. */
    private static void runConcurrently(int threads, ThreadJob job) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int thread = t;
            workers.add(Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                    job.run(thread);
                } catch (Throwable e) {
                    failures.add(e);
                }
            }));
        }
        start.countDown();
        for (Thread worker : workers) {
            assertThat(worker.join(Duration.ofSeconds(120))).isTrue();
        }
        assertThat(failures).isEmpty();
    }

    /** v0.0.20 🍊 Work done by one numbered thread. */
    @FunctionalInterface
    private interface ThreadJob {
        /** v0.0.20 🍊 Runs the job for thread number {@code thread}. */
        void run(int thread) throws Exception;
    }

    /** v0.0.20 🍊 Creates a one-item list for Lime, checks the item and requests approval. */
    private TaskList finishedList(String goal, Actor publisher) {
        TaskList list = taskLists.create(lime.agentId(), goal, publisher, null, List.of("Do it"));
        taskLists.apply(lime.agentId(), List.of(TaskOp.check(itemId(list, 1), null)));
        return taskLists.requestApproval(lime.agentId());
    }

    /** v0.0.20 🍊 The id of item number {@code ord}. */
    private static String itemId(TaskList list, int ord) {
        return list.itemAt(ord).orElseThrow().id();
    }

    /** v0.0.20 🍊 The stored optimistic version of one of Lime's lists. */
    private int version(String listId) {
        return jdbc.sql("SELECT version FROM task_list WHERE agent_id = :agentId AND id = :id")
                .param("agentId", lime.agentId().value()).param("id", listId).query(Integer.class).single();
    }

    /** v0.0.20 🍊 Number of stored items of one of Lime's lists. */
    private long itemRows(String listId) {
        return jdbc.sql("SELECT COUNT(*) FROM task_item WHERE agent_id = :agentId AND list_id = :listId")
                .param("agentId", lime.agentId().value()).param("listId", listId).query(Long.class).single();
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
