package ai.yuzu.internal;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.internal.cognition.HabitIndexService;
import ai.yuzu.internal.memory.LearningModule;
import ai.yuzu.internal.memory.MemoryConflict;
import ai.yuzu.internal.memory.MemoryConflictRepository;
import ai.yuzu.internal.memory.MemoryModule;
import ai.yuzu.internal.memory.MemoryOutcome;
import ai.yuzu.internal.subconscious.ConflictTracker;
import ai.yuzu.internal.subconscious.SubconsciousOutput;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.settings.LlmProvider;
import ai.yuzu.settings.SettingsService;
import ai.yuzu.support.FakeLlmServer;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.support.TestRooms;
import ai.yuzu.tool.impl.memory.MemoryReadTool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static ai.yuzu.support.FakeLlmServer.completion;
import static ai.yuzu.support.FakeLlmServer.schema;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * v0.0.28 🍊 S30 / S31: the learning and memory modules write habit and deep memory through the judge, hold
 * conflicts for 10 rounds (conflict → resolution → expiry) and keep recall working for fresh memories.
 */
@IntegrationTest
class MemoryWriteIntegrationTest {

    @Autowired
    private AgentService agents;
    @Autowired
    private AgentRuntimeManager runtimes;
    @Autowired
    private SettingsService settings;
    @Autowired
    private LearningModule learning;
    @Autowired
    private MemoryModule memory;
    @Autowired
    private ConflictTracker conflicts;
    @Autowired
    private MemoryConflictRepository conflictRepository;
    @Autowired
    private HabitIndexService habitIndex;
    @Autowired
    private MemoryReadTool memoryRead;
    @Autowired
    private ModuleDeps deps;
    @Autowired
    private NaturalTime time;
    @Autowired
    private JdbcClient jdbc;

    private FakeLlmServer server;
    private AgentProfile lime;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeLlmServer();
        settings.update(LlmProvider.CUSTOM, server.baseUrl(), null);
        settings.saveApiKey("sk-fake-provider-key-8888");
        String roomId = TestRooms.create(jdbc);
        lime = agents.createNamed(roomId, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null), "Lime");
    }

    @AfterEach
    void tearDown() {
        server.close();
        settings.update(LlmProvider.OPENAI, null, null);
    }

    /** v0.0.28 🍊 The first habit needs no judge; the cognition index sees it immediately (cache invalidated). */
    @Test
    void firstHabitIsStoredWithoutAskingTheJudge() {
        assertThat(habitIndex.index(lime.agentId())).doesNotContain("competitor");
        MemoryOutcome outcome = learning.learn(ctx(), "Competitor sweep",
                "when Alice asks me for a competitor brief",
                "start from the pricing pages of the three biggest rivals");
        assertThat(outcome.outcome()).isEqualTo(MemoryOutcome.Outcome.CREATED);
        assertThat(server.requests()).isEmpty();
        assertThat(habitIndex.index(lime.agentId())).contains("Competitor sweep")
                .contains("when Alice asks me for a competitor brief");
    }

    /** v0.0.28 🍊 The very same habit twice is caught by content_hash, so the judge is never asked. */
    @Test
    void identicalCandidateIsDeduplicatedByHash() {
        learning.learn(ctx(), "Competitor sweep", "when Alice asks me for a competitor brief",
                "start from the pricing pages of the three biggest rivals");
        MemoryOutcome again = learning.learn(ctx(), "Competitor sweep  ",
                "when Alice asks me for a competitor brief",
                "start from the pricing pages of the three biggest rivals");
        assertThat(again.outcome()).isEqualTo(MemoryOutcome.Outcome.DUPLICATE);
        assertThat(server.requests()).isEmpty();
        assertThat(rows("habit_memory")).isEqualTo(1);
    }

    /** v0.0.28 🍊 A similar habit goes to the judge, which merges it into the existing entry. */
    @Test
    void similarHabitIsMergedIntoTheExistingEntry() {
        learning.learn(ctx(), "Competitor sweep", "when Alice asks me for a competitor brief",
                "start from the pricing pages of the three biggest rivals");
        String targetId = one("SELECT id FROM habit_memory WHERE agent_id = ?");
        server.enqueueFor(schema("memory_judge"), completion(judgement("DUPLICATE", targetId, "MERGE",
                "start from the pricing pages of the three biggest rivals, then read their launch blogs", null),
                1500, 0, 40));

        MemoryOutcome outcome = learning.learn(ctx(), "Competitor sweep", "when Alice asks me for a competitor brief",
                "also read the launch blogs of the rivals");

        assertThat(outcome.outcome()).isEqualTo(MemoryOutcome.Outcome.MERGED);
        assertThat(rows("habit_memory")).isEqualTo(1);
        assertThat(one("SELECT technique FROM habit_memory WHERE agent_id = ?")).contains("launch blogs");
        assertThat(server.requests()).anyMatch(b -> b.contains(schema("memory_judge")));
    }

    /** v0.0.28 🍊 Conflict path: the old memory is kept and the conflict waits 10 rounds. */
    @Test
    void conflictIsHeldAndTheOldMemoryIsKept() {
        learning.learn(ctx(), "Brief format", "when I write a research brief",
                "Alice wants the brief as a bullet list");
        String targetId = one("SELECT id FROM habit_memory WHERE agent_id = ?");
        server.enqueueFor(schema("memory_judge"), completion(judgement("CONFLICT", targetId, "IGNORE", null,
                "Alice now asks for prose, which contradicts the bullet list habit"), 1500, 0, 40));

        MemoryOutcome outcome = learning.learn(ctx(), "Brief format", "when I write a research brief",
                "Alice wants the brief written as prose");

        assertThat(outcome.outcome()).isEqualTo(MemoryOutcome.Outcome.CONFLICT);
        assertThat(rows("habit_memory")).isEqualTo(1);
        assertThat(one("SELECT technique FROM habit_memory WHERE agent_id = ?")).contains("bullet list");
        List<MemoryConflict> open = conflictRepository.open(lime.agentId());
        assertThat(open).hasSize(1);
        assertThat(open.getFirst().roundsLeft()).isEqualTo(ConflictTracker.HOLD_ROUNDS);
        assertThat(open.getFirst().targetId()).isEqualTo(targetId);
        assertThat(conflicts.render(lime.agentId())).contains("prose");
    }

    /** v0.0.28 🍊 Resolution path: USE_NEW supersedes the old entry and closes the conflict. */
    @Test
    void conflictIsResolvedByTheSubconscious() {
        String conflictId = openConflict();
        String targetId = conflictRepository.open(lime.agentId()).getFirst().targetId();

        conflicts.activate(ctx(), List.of(new SubconsciousOutput.ConflictUpdate(conflictId,
                SubconsciousOutput.Resolution.USE_NEW, null, "Alice confirmed she wants prose now")));

        assertThat(conflictRepository.open(lime.agentId())).isEmpty();
        assertThat(status(conflictId)).isEqualTo("RESOLVED");
        assertThat(one("SELECT status FROM habit_memory WHERE agent_id = ? AND id = '" + targetId + "'"))
                .isEqualTo("SUPERSEDED");
        assertThat(one("SELECT technique FROM habit_memory WHERE agent_id = ? AND status = 'ACTIVE'"))
                .contains("prose");
    }

    /** v0.0.28 🍊 Expiry path: after 10 unresolved rounds the conflict expires and the old memory stays. */
    @Test
    void conflictExpiresAfterTenRoundsAndKeepsTheOldMemory() {
        String conflictId = openConflict();

        for (int round = 1; round < ConflictTracker.HOLD_ROUNDS; round++) {
            conflicts.activate(ctx(), List.of());
            assertThat(conflictRepository.open(lime.agentId())).as("round " + round).hasSize(1);
            assertThat(conflictRepository.open(lime.agentId()).getFirst().roundsLeft())
                    .isEqualTo(ConflictTracker.HOLD_ROUNDS - round);
        }
        conflicts.activate(ctx(), List.of());

        assertThat(conflictRepository.open(lime.agentId())).isEmpty();
        assertThat(status(conflictId)).isEqualTo("EXPIRED");
        assertThat(one("SELECT technique FROM habit_memory WHERE agent_id = ? AND status = 'ACTIVE'"))
                .contains("bullet list");
    }

    /** v0.0.28 🍊 S31: a deep memory is deduplicated by content_hash and can be recalled right away. */
    @Test
    void deepMemoryIsDeduplicatedAndImmediatelyRecallable() {
        MemoryOutcome first = memory.remember(ctx(), "Citrus Spark launch",
                "The Citrus Spark bottle launches on Friday with three beta customers.",
                List.of("Citrus Spark", "launch"), "SUBCONSCIOUS");
        assertThat(first.outcome()).isEqualTo(MemoryOutcome.Outcome.CREATED);

        MemoryOutcome again = memory.remember(ctx(), "Citrus Spark launch",
                "The Citrus Spark bottle launches on Friday with three beta customers.",
                List.of("Citrus Spark"), "SUBCONSCIOUS");
        assertThat(again.outcome()).isEqualTo(MemoryOutcome.Outcome.DUPLICATE);
        assertThat(rows("deep_memory")).isEqualTo(1);
        assertThat(server.requests()).isEmpty();

        ToolResult recalled = memoryRead.execute(toolContext(),
                new MemoryReadTool.Args("the Citrus Spark launch", null, List.of("Citrus Spark", "launch")));
        assertThat(recalled.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(recalled.output()).contains("three beta customers");
    }

    /** v0.0.28 🍊 A fresh deep memory does not break the existing time-window lookup. */
    @Test
    void timeWindowRecallStillWorksAfterAWrite() {
        server.defaultFor(schema("memory_judge"),
                completion(judgement("NEW", null, "IGNORE", null, null), 1200, 0, 20));
        memory.remember(ctx(), "Old budget note", "Alice asked about the budget a long time ago.",
                List.of("budget"), "SUBCONSCIOUS");
        backdate("Old budget note", 40);
        memory.remember(ctx(), "Beta customer list", "Alice asked me to line up three beta customers.",
                List.of("beta", "customers"), "SUBCONSCIOUS");
        backdate("Beta customer list", 5);

        ToolResult recalled = memoryRead.execute(toolContext(),
                new MemoryReadTool.Args("what did Alice ask 5 minutes ago", "5 minutes ago", List.of()));

        assertThat(recalled.output()).contains("three beta customers").doesNotContain("budget")
                .contains("5 minutes ago");
    }

    /** v0.0.28 🍊 Moves one deep memory back in time so a time-window recall can be exercised. */
    private void backdate(String title, int minutes) {
        jdbc.sql("""
                        UPDATE deep_memory SET created_at = DATE_SUB(UTC_TIMESTAMP(3), INTERVAL :m MINUTE)
                        WHERE agent_id = :a AND title = :t
                        """).param("a", lime.agentId().value()).param("t", title).param("m", minutes).update();
    }

    /** v0.0.28 🍊 Opens one habit conflict and returns its id. */
    private String openConflict() {
        learning.learn(ctx(), "Brief format", "when I write a research brief",
                "Alice wants the brief as a bullet list");
        String targetId = one("SELECT id FROM habit_memory WHERE agent_id = ?");
        server.enqueueFor(schema("memory_judge"), completion(judgement("CONFLICT", targetId, "IGNORE", null,
                "Alice now asks for prose"), 1500, 0, 40));
        learning.learn(ctx(), "Brief format", "when I write a research brief",
                "Alice wants the brief written as prose");
        return conflictRepository.open(lime.agentId()).getFirst().id();
    }

    /** v0.0.28 🍊 A scripted judge answer. */
    private static String judgement(String verdict, String targetId, String action, String mergedText,
                                    String conflictReason) {
        return "{\"reasoning\":\"scripted\",\"verdict\":\"" + verdict + "\",\"targetId\":"
                + quote(targetId) + ",\"action\":\"" + action + "\",\"mergedText\":" + quote(mergedText)
                + ",\"conflictReason\":" + quote(conflictReason) + "}";
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private AgentContext ctx() {
        return runtimes.require(lime.agentId()).context("trace-memory", null, time);
    }

    private ToolContext toolContext() {
        return new ToolContext(ctx(), "batch-test", "call-test", 0, "recall", 0,
                deps.reporter().start(lime.agentId(), "TOOL", "test", "trace-memory", null));
    }

    private int rows(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE agent_id = ?")
                .param(lime.agentId().value()).query(Integer.class).single();
    }

    private String one(String sql) {
        return jdbc.sql(sql).param(lime.agentId().value()).query(String.class).single();
    }

    private String status(String conflictId) {
        return jdbc.sql("SELECT status FROM memory_conflict WHERE agent_id = ? AND id = ?")
                .param(lime.agentId().value()).param(conflictId).query(String.class).single();
    }
}
