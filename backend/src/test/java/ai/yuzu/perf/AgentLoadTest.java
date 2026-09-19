package ai.yuzu.perf;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.runtime.AgentRuntime;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.chat.ChatService;
import ai.yuzu.common.concurrent.ErrorSink;
import ai.yuzu.common.error.CancelledException;
import ai.yuzu.external.chat.ChatInbox;
import ai.yuzu.internal.consciousness.ConsciousnessPool;
import ai.yuzu.internal.consciousness.MainConsciousnessService;
import ai.yuzu.internal.consciousness.MainRunHandler;
import ai.yuzu.internal.consciousness.Origin;
import ai.yuzu.internal.subconscious.SubconsciousScheduler;
import ai.yuzu.room.HumanUserService;
import ai.yuzu.room.UserView;
import ai.yuzu.settings.LlmProvider;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.support.FakeLlmServer;
import ai.yuzu.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v0.0.31 🍊 Bulk-message load test: 8 agents in one room under a burst of human chat messages.
 *
 * <p>Everything is driven by the scripted {@link FakeLlmServer} (standing replies per module schema), so the
 * run is deterministic: no sleeps decide the outcome, only quiescence latches. Asserted: the system reaches
 * a quiet state (no deadlock), every pool message is consumed exactly once, at most one main consciousness
 * runs per agent at a time, no background task reported a failure, inbound dispatch latency stays inside a
 * stated bound and nothing is stranded once the room goes quiet.</p>
 */
@IntegrationTest
@Import(AgentLoadTest.Instrumentation.class)
class AgentLoadTest {

    private static final Logger log = LoggerFactory.getLogger(AgentLoadTest.class);

    /** v0.0.31 🍊 Number of agents in the room (the platform maximum). */
    private static final int AGENTS = 8;
    /** v0.0.31 🍊 Concurrent human posters. */
    private static final int POSTERS = 4;
    /** v0.0.31 🍊 Messages posted by each human. */
    private static final int MESSAGES_PER_POSTER = 15;
    /** v0.0.31 🍊 Writer threads per agent in the direct pool-pressure phase. */
    private static final int WRITERS_PER_AGENT = 3;
    /** v0.0.31 🍊 Pool messages each writer thread offers. */
    private static final int OFFERS_PER_WRITER = 10;
    /** v0.0.31 🍊 Upper bound for the whole burst to drain (generous; a deadlock blows through it). */
    private static final long QUIET_TIMEOUT_SECONDS = 120;
    /** v0.0.31 🍊 Stated bound for inbound dispatch latency (first human message → pool message of an agent). */
    private static final long MAX_DISPATCH_LATENCY_MILLIS = 20_000;

    /** v0.0.31 🍊 Wraps the real main-run handler to count per-agent concurrency and consumed pool messages. */
    @TestConfiguration
    static class Instrumentation {

        static final Map<String, AtomicInteger> ACTIVE = new ConcurrentHashMap<>();
        static final AtomicInteger MAX_CONCURRENT_PER_AGENT = new AtomicInteger();
        static final List<String> CONSUMED = new CopyOnWriteArrayList<>();
        static final AtomicInteger RUNS = new AtomicInteger();
        static final List<String> FAILURES = new CopyOnWriteArrayList<>();

        /** v0.0.31 🍊 Resets every counter between tests. */
        static void reset() {
            ACTIVE.clear();
            MAX_CONCURRENT_PER_AGENT.set(0);
            CONSUMED.clear();
            RUNS.set(0);
            FAILURES.clear();
        }

        /** v0.0.31 🍊 Counting decorator around the production main-run handler. */
        @Bean
        @Primary
        MainRunHandler countingMainRunHandler(MainConsciousnessService delegate) {
            return (agentId, batch) -> {
                AtomicInteger active = ACTIVE.computeIfAbsent(agentId.value(), k -> new AtomicInteger());
                MAX_CONCURRENT_PER_AGENT.accumulateAndGet(active.incrementAndGet(), Math::max);
                try {
                    batch.forEach(m -> CONSUMED.add(m.id()));
                    RUNS.incrementAndGet();
                    delegate.runOnce(agentId, batch);
                } finally {
                    active.decrementAndGet();
                }
            };
        }

        /** v0.0.31 🍊 Records every background failure the guarded async runner reports. */
        @Bean
        ErrorSink loadTestErrorSink() {
            return (context, agentId, error) -> {
                if (!(error instanceof CancelledException)) {
                    FAILURES.add(context + "/" + agentId + ": " + error);
                }
            };
        }
    }

    @Autowired
    private AgentService agents;
    @Autowired
    private HumanUserService users;
    @Autowired
    private ChatService chat;
    @Autowired
    private SettingsService settings;
    @Autowired
    private AgentRuntimeManager runtimes;
    @Autowired
    private JdbcClient jdbc;

    private FakeLlmServer server;
    private String roomId;
    private final List<AgentProfile> room = new ArrayList<>();
    private UserView alice;
    private UserView bob;

    @BeforeEach
    void setUp() throws Exception {
        Instrumentation.reset();
        room.clear();
        server = new FakeLlmServer();
        // Standing replies: every module always answers, whatever the interleaving is.
        server.defaultFor(FakeLlmServer.schema("chat"), FakeLlmServer.completion(
                "{\"reasoning\":\"this is work for me\",\"decision\":\"FORWARD\",\"replyText\":null,"
                        + "\"ackText\":null,\"topicClosed\":false}", 1400, 1024, 20));
        server.defaultFor(FakeLlmServer.schema("safety"), FakeLlmServer.completion(
                "{\"reasoning\":\"clean\",\"verdict\":\"SAFE\",\"violations\":[],\"masks\":[],"
                        + "\"userFacingReason\":null}", 1500, 1024, 10));
        server.defaultFor(FakeLlmServer.schema("main"), FakeLlmServer.completion(
                "{\"thought\":\"noted, nothing to do\",\"mode\":\"END\",\"actions\":[],\"nextThought\":null}",
                3000, 2048, 30));
        server.defaultFor(FakeLlmServer.schema("wm_compactor"), FakeLlmServer.completion(
                "{\"summary\":\"I read a batch of sprint updates about the Citrus Spark bottle.\"}", 2000, 0, 40));
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-3434");
        roomId = String.format("room-%04x", ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        jdbc.sql("INSERT INTO room (id, name, created_at) VALUES (:id, 'Load room', UTC_TIMESTAMP(3))")
                .param("id", roomId).update();
        String[] names = {"Yuzu", "Lime", "Kumquat", "Pomelo", "Citron", "Sudachi", "Calamansi", "Bergamot"};
        for (int i = 0; i < AGENTS; i++) {
            room.add(agents.createNamed(roomId, new CreateAgentRequest(Role.values()[i % Role.values().length],
                    null, null, null, null, null), names[i]));
        }
        alice = users.join(roomId, "Alice");
        bob = users.join(roomId, "Bob");
    }

    @AfterEach
    void tearDown() {
        room.forEach(p -> agents.retire(p.agentId()));
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.31 🍊 40 messages, 4 posters, 8 agents: no deadlock, exactly-once, one run per agent, nothing stranded. */
    @Test
    void burstOfMessagesAcrossEightAgents() throws Exception {
        List<String> agentIds = room.stream().map(p -> p.agentId().value()).toList();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> posters = new ArrayList<>();
        for (int p = 0; p < POSTERS; p++) {
            int poster = p;
            posters.add(Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                String userId = poster % 2 == 0 ? alice.id() : bob.id();
                for (int i = 0; i < MESSAGES_PER_POSTER; i++) {
                    String mention = i % 3 == 0 ? "@" + room.get((poster + i) % AGENTS).name() + " " : "";
                    chat.postHuman(roomId, userId, mention + "Sprint update " + poster + "-" + i
                            + ": the Citrus Spark bottle ships Friday.");
                }
            }));
        }
        long startedNanos = System.nanoTime();
        start.countDown();
        for (Thread t : posters) {
            t.join();
        }
        long postedNanos = System.nanoTime();
        awaitQuiet();
        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000;

        int stored = assertHealthyAndQuiet(agentIds);
        assertThat(consumedAgents(agentIds)).containsExactlyInAnyOrderElementsOf(agentIds);

        // Dispatch latency: first human message → first pool message of each agent.
        List<Long> latencies = dispatchLatenciesMillis(agentIds);
        assertThat(latencies).hasSize(AGENTS);
        long worst = latencies.stream().mapToLong(Long::longValue).max().orElseThrow();
        long p95 = latencies.get(Math.min(latencies.size() - 1, (int) Math.ceil(latencies.size() * 0.95) - 1));
        log.info("🍊 chat burst: {} messages, {} agents, {} main runs, {} pool messages, {} model requests in {} ms"
                        + " (posting took {} ms); dispatch latency p50={} ms p95={} ms max={} ms",
                POSTERS * MESSAGES_PER_POSTER, AGENTS, Instrumentation.RUNS.get(), stored,
                server.requests().size(), elapsedMillis, (postedNanos - startedNanos) / 1_000_000,
                latencies.get(latencies.size() / 2), p95, worst);
        assertThat(worst).as("worst inbound dispatch latency (ms)").isLessThanOrEqualTo(MAX_DISPATCH_LATENCY_MILLIS);
    }

    /**
     * v0.0.31 🍊 Direct pool pressure: 8 agents × 3 writer threads × 10 messages, all 8 main slots contended.
     *
     * <p>This bypasses the chat debounce on purpose, so every message really reaches the main loop and the
     * {@code PriorityGate} MAIN_TOOL class (8 permits) is saturated by 8 agents at once.</p>
     */
    @Test
    void bulkPoolMessagesSaturateEveryMainLoop() throws Exception {
        List<String> agentIds = room.stream().map(p -> p.agentId().value()).toList();
        int expected = AGENTS * WRITERS_PER_AGENT * OFFERS_PER_WRITER;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> writers = new ArrayList<>();
        for (AgentProfile profile : room) {
            for (int w = 0; w < WRITERS_PER_AGENT; w++) {
                int writer = w;
                writers.add(Thread.ofVirtual().start(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int i = 0; i < OFFERS_PER_WRITER; i++) {
                        runtimes.require(profile.agentId()).consciousness().offer(Origin.EXTERNAL,
                                "Alice (human) told me in the group chat",
                                "Bulk stimulus " + writer + "-" + i + " for " + profile.name(), null, 0, false);
                    }
                }));
            }
        }
        long startedNanos = System.nanoTime();
        start.countDown();
        for (Thread t : writers) {
            t.join();
        }
        awaitQuiet();
        long elapsedMillis = Math.max(1, (System.nanoTime() - startedNanos) / 1_000_000);

        int stored = assertHealthyAndQuiet(agentIds);
        assertThat(stored).isEqualTo(expected);
        log.info("🍊 pool burst: {} pool messages over {} agents drained by {} main runs in {} ms ({} msg/s)",
                expected, AGENTS, Instrumentation.RUNS.get(), elapsedMillis, expected * 1000L / elapsedMillis);
    }

    /**
     * v0.0.31 🍊 Shared health check: exactly-once consumption, one run per agent, no failures, nothing stranded.
     *
     * @return the number of pool messages stored for these agents
     */
    private int assertHealthyAndQuiet(List<String> agentIds) {
        assertThat(Instrumentation.RUNS.get()).isGreaterThanOrEqualTo(AGENTS);
        assertThat(Instrumentation.MAX_CONCURRENT_PER_AGENT.get()).isEqualTo(1);

        List<String> consumed = List.copyOf(Instrumentation.CONSUMED);
        assertThat(new HashSet<>(consumed)).as("a pool message was consumed twice").hasSameSizeAs(consumed);
        Set<String> stored = new HashSet<>(jdbc.sql("SELECT id FROM pool_message WHERE agent_id IN (:ids)")
                .param("ids", agentIds).query(String.class).list());
        assertThat(new HashSet<>(consumed)).isEqualTo(stored);
        Long unconsumed = jdbc.sql("SELECT COUNT(*) FROM pool_message WHERE agent_id IN (:ids) AND run_id IS NULL")
                .param("ids", agentIds).query(Long.class).single();
        assertThat(unconsumed).isZero();

        assertThat(Instrumentation.FAILURES).isEmpty();

        for (AgentRuntime runtime : liveRuntimes()) {
            ConsciousnessPool pool = runtime.consciousness().pool();
            assertThat(pool.isRunning()).as("pool of %s still running", runtime.agentId()).isFalse();
            assertThat(pool.triggerCount()).as("triggers left for %s", runtime.agentId()).isZero();
            assertThat(pool.size()).as("messages left for %s", runtime.agentId()).isZero();
            assertThat(runtime.component(ChatInbox.class).pendingCount()).isZero();
            assertThat(runtime.consciousness().subconscious().availablePermits())
                    .isEqualTo(SubconsciousScheduler.MAX_CONCURRENT);
        }
        Long openBatches = jdbc.sql("SELECT COUNT(*) FROM action_batch WHERE agent_id IN (:ids)"
                        + " AND status IN ('REVIEWING','DISPATCHING')").param("ids", agentIds)
                .query(Long.class).single();
        assertThat(openBatches).isZero();
        return stored.size();
    }

    /** v0.0.31 🍊 Ids of this room's agents that consumed at least one pool message. */
    private List<String> consumedAgents(List<String> agentIds) {
        return jdbc.sql("SELECT DISTINCT agent_id FROM pool_message WHERE agent_id IN (:ids) AND run_id IS NOT NULL")
                .param("ids", agentIds).query(String.class).list();
    }

    /** v0.0.31 🍊 Sorted per-agent latency in milliseconds between the first chat message and the first pool message. */
    private List<Long> dispatchLatenciesMillis(List<String> agentIds) {
        return jdbc.sql("""
                        SELECT TIMESTAMPDIFF(MICROSECOND,
                                   (SELECT MIN(created_at) FROM chat_message WHERE room_id = :room),
                                   MIN(created_at)) DIV 1000 AS ms
                        FROM pool_message WHERE agent_id IN (:ids) GROUP BY agent_id ORDER BY ms
                        """)
                .param("room", roomId).param("ids", agentIds).query(Long.class).list();
    }

    /** v0.0.31 🍊 Runtimes of the agents created by this test. */
    private List<AgentRuntime> liveRuntimes() {
        return room.stream().map(p -> runtimes.require(p.agentId())).toList();
    }

    /** v0.0.31 🍊 Waits until every agent is idle and stays idle, so late work cannot hide behind the check. */
    private void awaitQuiet() throws InterruptedException {
        awaitTrue(this::idle);
        // Hold the condition: a single observation could fall between two hand-offs.
        for (int i = 0; i < 25; i++) {
            Thread.sleep(20);
            if (!idle()) {
                awaitTrue(this::idle);
                i = 0;
            }
        }
    }

    /** v0.0.31 🍊 True when no inbox, pool, main loop or subconscious of the room has work left. */
    private boolean idle() {
        for (AgentRuntime runtime : liveRuntimes()) {
            ConsciousnessPool pool = runtime.consciousness().pool();
            if (pool.isRunning() || pool.triggerCount() > 0 || pool.size() > 0
                    || runtime.component(ChatInbox.class).pendingCount() > 0
                    || runtime.consciousness().subconscious().availablePermits() != SubconsciousScheduler.MAX_CONCURRENT) {
                return false;
            }
        }
        return Instrumentation.ACTIVE.values().stream().allMatch(a -> a.get() == 0);
    }

    /** v0.0.31 🍊 Polls a condition up to the quiet timeout (a deadlock fails here instead of hanging forever). */
    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(QUIET_TIMEOUT_SECONDS);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the room did not go quiet within " + QUIET_TIMEOUT_SECONDS + " s");
            }
            Thread.sleep(10);
        }
    }
}
