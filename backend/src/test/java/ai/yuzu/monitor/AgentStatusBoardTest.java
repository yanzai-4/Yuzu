package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.IdGen;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static ai.yuzu.monitor.EventPhase.CANCELLED;
import static ai.yuzu.monitor.EventPhase.END;
import static ai.yuzu.monitor.EventPhase.ERROR;
import static ai.yuzu.monitor.EventPhase.START;
import static ai.yuzu.monitor.EventPhase.STATE;
import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.12 🍊 Status board: desk-state priorities, concurrent spans, throttled publishing, sticky errors and summarizer rules. */
class AgentStatusBoardTest {

    private static final MonitorTimings FAST = new MonitorTimings(Duration.ofMillis(100), Duration.ofMillis(300),
            Duration.ofMillis(300), Duration.ofHours(1), 20, 256);

    private BoardFixture fixture;

    /** v0.0.12 🍊 Stops the fixture's hub and timer. */
    @AfterEach
    void tearDown() {
        if (fixture != null) {
            fixture.close();
        }
    }

    /** v0.0.12 🍊 The derived state follows ERROR > PAUSED > WAITING > TALKING > THINKING > WORKING > IDLE. */
    @Test
    void derivedStatePriorities() throws Exception {
        fixture = new BoardFixture(new CodeBubbleSummarizer(), FAST);
        AgentStatusBoard board = fixture.board;
        AgentId agent = fixture.agent();
        board.register(agent, BoardFixture.ROOM, false);
        assertStatus(board.status(agent), "IDLE", BubbleText.IDLE_MODULE, BubbleText.IDLE_SUMMARY);

        board.apply(fixture.event(agent, ModuleKind.TOOL, START, "span-tool", "Searching the web for yuzu prices"),
                null);
        assertStatus(board.status(agent), "WORKING", "Tool", "Searching the web for yuzu prices");

        board.apply(fixture.event(agent, ModuleKind.MAIN, START, "span-main", "Deciding what to tell Alice"), null);
        assertStatus(board.status(agent), "THINKING", "Main consciousness", "Deciding what to tell Alice");
        assertThat(board.status(agent).activeModules()).containsExactly("MAIN", "TOOL");

        board.apply(fixture.event(agent, ModuleKind.CHAT, START, "span-chat", "Replying to Alice"), DeskState.TALKING);
        assertStatus(board.status(agent), "TALKING", "Chat", "Replying to Alice");

        board.setWaiting(agent, true, "Waiting for Alice to approve the trade");
        assertStatus(board.status(agent), "WAITING", BubbleText.WAITING_MODULE,
                "Waiting for Alice to approve the trade");

        board.setPaused(agent, true);
        assertStatus(board.status(agent), "PAUSED", BubbleText.PAUSED_MODULE, BubbleText.PAUSED_SUMMARY);

        board.apply(fixture.event(agent, ModuleKind.TOOL, ERROR, "span-tool", "Failed: The provider timed out"),
                null);
        assertStatus(board.status(agent), "ERROR", "Tool", "Failed: The provider timed out");
        assertThat(board.status(agent).activeModules()).containsExactly("CHAT", "MAIN");

        Thread.sleep(400);
        assertThat(board.status(agent).state()).isEqualTo("PAUSED");
        board.setPaused(agent, false);
        assertThat(board.status(agent).state()).isEqualTo("WAITING");
        board.setWaiting(agent, false, null);
        assertThat(board.status(agent).state()).isEqualTo("TALKING");
        board.apply(fixture.event(agent, ModuleKind.CHAT, END, "span-chat", "Posted"), DeskState.TALKING);
        assertThat(board.status(agent).state()).isEqualTo("THINKING");
        board.apply(fixture.event(agent, ModuleKind.MAIN, END, "span-main", "done"), null);
        assertStatus(board.status(agent), "IDLE", BubbleText.IDLE_MODULE, BubbleText.IDLE_SUMMARY);
        assertThat(fixture.asyncFailures).isEmpty();
    }

    /** v0.0.12 🍊 A failure shows ERROR for the hold time, then the board publishes the recovered state by itself. */
    @Test
    void errorIsStickyThenClearsByItself() throws Exception {
        fixture = new BoardFixture(new CodeBubbleSummarizer(), FAST);
        AgentId agent = fixture.agent();
        fixture.board.register(agent, BoardFixture.ROOM, false);
        fixture.board.apply(fixture.event(agent, ModuleKind.TOOL, START, "span-1", "Sending the weekly report"), null);
        fixture.board.apply(fixture.event(agent, ModuleKind.TOOL, ERROR, "span-1", "Failed: SMTP refused"), null);
        long failedAt = System.nanoTime();
        fixture.recorder.await("ERROR published", () -> fixture.lastPublishedState(agent).equals("ERROR"));
        fixture.recorder.await("ERROR cleared", () -> fixture.lastPublishedState(agent).equals("IDLE"));
        assertThat((System.nanoTime() - failedAt) / 1_000_000).isGreaterThanOrEqualTo(250);
    }

    /** v0.0.12 🍊 Several spans of one module keep it active until the last one ends, even under heavy concurrency. */
    @Test
    void concurrentSpansOfOneModuleAreCounted() throws Exception {
        fixture = new BoardFixture(new CodeBubbleSummarizer(), FAST);
        AgentStatusBoard board = fixture.board;
        AgentId agent = fixture.agent();
        board.apply(fixture.event(agent, ModuleKind.SUBCONSCIOUS, START, "span-a", "Checking Main's plan"), null);
        board.apply(fixture.event(agent, ModuleKind.SUBCONSCIOUS, START, "span-b", "Looking for a habit to learn"),
                null);
        board.apply(fixture.event(agent, ModuleKind.SUBCONSCIOUS, END, "span-a", "done"), null);
        assertStatus(board.status(agent), "THINKING", "Subconscious", "Looking for a habit to learn");
        assertThat(board.status(agent).activeModules()).containsExactly("SUBCONSCIOUS");
        board.apply(fixture.event(agent, ModuleKind.SUBCONSCIOUS, CANCELLED, "span-b", "paused"), null);
        assertThat(board.status(agent).state()).isEqualTo("IDLE");

        CountDownLatch go = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < 64; t++) {
            int thread = t;
            workers.add(Thread.ofVirtual().start(() -> {
                awaitQuietly(go);
                for (int i = 0; i < 20; i++) {
                    String span = "span-" + thread + "-" + i;
                    board.apply(fixture.event(agent, ModuleKind.SUBCONSCIOUS, START, span, "Thought " + i), null);
                    board.apply(fixture.event(agent, ModuleKind.SUBCONSCIOUS, STATE, span, "Still thinking"), null);
                    board.apply(fixture.event(agent, ModuleKind.SUBCONSCIOUS, END, span, "done"), null);
                }
            }));
        }
        go.countDown();
        for (Thread worker : workers) {
            worker.join();
        }
        assertThat(board.status(agent).state()).isEqualTo("IDLE");
        assertThat(board.status(agent).activeModules()).isEmpty();
        assertThat(fixture.asyncFailures).isEmpty();
    }

    /** v0.0.12 🍊 A burst of updates yields at most ~4 agent.status events per second, and the last one is the latest state. */
    @Test
    void statusPublishingIsThrottledAndTheLatestStateWins() throws Exception {
        fixture = new BoardFixture(new CodeBubbleSummarizer(), MonitorTimings.DEFAULTS);
        AgentStatusBoard board = fixture.board;
        AgentId agent = fixture.agent();
        board.register(agent, BoardFixture.ROOM, false);
        fixture.recorder.await("initial status", () -> !fixture.statuses(agent).isEmpty());
        int before = fixture.statuses(agent).size();

        long started = System.nanoTime();
        for (int i = 1; i <= 1_000; i++) {
            board.setPoolSize(agent, i);
            if (i % 100 == 0) {
                Thread.sleep(100);
            }
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        fixture.recorder.await("latest pool size delivered", () -> {
            List<JsonNode> received = fixture.statuses(agent);
            return received.get(received.size() - 1).get("poolSize").asInt() == 1_000;
        });
        int published = fixture.statuses(agent).size() - before;
        assertThat(published).isBetween(2, (int) (elapsedMillis / 250) + 3);
        Thread.sleep(400);
        assertThat(fixture.statuses(agent).size() - before).isEqualTo(published);
    }

    /** v0.0.12 🍊 Unchanged statuses are re-sent periodically, so a client that missed one (no replay) converges. */
    @Test
    void unchangedStatusesAreRefreshedPeriodically() throws Exception {
        fixture = new BoardFixture(new CodeBubbleSummarizer(), new MonitorTimings(Duration.ofMillis(100),
                Duration.ofMillis(300), Duration.ofMillis(300), Duration.ofMillis(400), 20, 256));
        AgentId agent = fixture.agent();
        fixture.board.register(agent, BoardFixture.ROOM, false);
        fixture.recorder.await("initial status", () -> !fixture.statuses(agent).isEmpty());
        fixture.recorder.await("re-sent without any change", () -> fixture.statuses(agent).size() >= 3);
        assertThat(fixture.statuses(agent)).extracting(status -> status.get("state").asText()).containsOnly("IDLE");
    }

    /** v0.0.12 🍊 The summarizer runs at most once per interval, only after changes, and never while idle. */
    @Test
    void summarizerIsThrottledAndNeverCalledWhileIdle() throws Exception {
        CountingSummarizer summarizer = new CountingSummarizer(null);
        fixture = new BoardFixture(summarizer, FAST);
        AgentStatusBoard board = fixture.board;
        AgentId agent = fixture.agent();
        board.register(agent, BoardFixture.ROOM, false);
        for (int i = 0; i < 20; i++) {
            board.setPoolSize(agent, i);
        }
        board.setWaiting(agent, true, "Waiting for Bob");
        board.setWaiting(agent, false, null);
        Thread.sleep(400);
        assertThat(summarizer.calls.get()).isZero();

        board.apply(fixture.event(agent, ModuleKind.MAIN, START, "span-main", "Thinking about the launch plan"), null);
        fixture.recorder.await("first summary", () -> summarizer.calls.get() == 1);
        long started = System.nanoTime();
        for (int i = 0; i < 40; i++) {
            board.apply(fixture.event(agent, ModuleKind.MAIN, STATE, "span-main", "Step " + i), null);
            Thread.sleep(25);
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        Thread.sleep(450);
        int calls = summarizer.calls.get();
        assertThat(calls).isBetween(2, (int) (elapsedMillis / 300) + 3);
        assertThat(summarizer.lastDefault.get()).isEqualTo("Step 39");

        Thread.sleep(700);
        assertThat(summarizer.calls.get()).as("nothing changed").isEqualTo(calls);
        board.apply(fixture.event(agent, ModuleKind.MAIN, END, "span-main", "done"), null);
        Thread.sleep(700);
        assertThat(summarizer.calls.get()).as("idle again").isEqualTo(calls);
    }

    /** v0.0.12 🍊 A duplicate STATE leaves the desk unchanged, so it must not spend another LIGHT-model summary. */
    @Test
    void duplicateStateDoesNotTriggerAnotherSummary() throws Exception {
        CountingSummarizer summarizer = new CountingSummarizer(null);
        fixture = new BoardFixture(summarizer, FAST);
        AgentStatusBoard board = fixture.board;
        AgentId agent = fixture.agent();

        board.apply(fixture.event(agent, ModuleKind.MAIN, START, "span-main", "Drafting the launch plan"), null);
        fixture.recorder.await("first summary", () -> summarizer.calls.get() == 1);
        Thread.sleep(400);

        board.apply(fixture.event(agent, ModuleKind.MAIN, STATE, "span-main", "Drafting the launch plan"), null);
        Thread.sleep(450);

        assertThat(summarizer.calls.get()).isEqualTo(1);
        assertStatus(board.status(agent), "THINKING", "Main consciousness", "Drafting the launch plan");
    }

    /** v0.0.12 🍊 With the code summarizer the bubble follows the latest START/STATE text immediately, clipped to ~80 chars. */
    @Test
    void codeSummaryFollowsTheLatestText() {
        fixture = new BoardFixture(new CodeBubbleSummarizer(), FAST);
        AgentStatusBoard board = fixture.board;
        AgentId agent = fixture.agent();
        board.apply(fixture.event(agent, ModuleKind.MAIN, START, "span-main", "Reading Alice's request"), null);
        board.apply(fixture.event(agent, ModuleKind.MAIN, STATE, "span-main", "Drafting the reply to Alice"), null);
        assertThat(board.status(agent).bubble().summary()).isEqualTo("Drafting the reply to Alice");
        board.apply(fixture.event(agent, ModuleKind.MAIN, STATE, "span-main",
                "Comparing the quarterly citrus prices of three wholesale markets before writing the summary"), null);
        String summary = board.status(agent).bubble().summary();
        assertThat(summary).hasSizeLessThanOrEqualTo(BubbleText.MAX_SUMMARY).endsWith("…")
                .startsWith("Comparing the quarterly citrus prices");
    }

    /** v0.0.12 🍊 A refined (AI) summary is pinned while the focus module stays; a new focus shows its code text first. */
    @Test
    void refinedSummariesArePinnedWhileTheFocusStays() throws Exception {
        String refined = "Researching citrus prices for Alice's weekly report";
        fixture = new BoardFixture(new CountingSummarizer(refined), FAST);
        AgentStatusBoard board = fixture.board;
        AgentId agent = fixture.agent();
        board.apply(fixture.event(agent, ModuleKind.TOOL, START, "span-tool", "GET https://example.test/prices?p=1"),
                null);
        fixture.recorder.await("refined summary pinned",
                () -> board.status(agent).bubble().summary().equals(refined));
        assertThat(board.status(agent).bubble().module()).isEqualTo("Tool");

        board.apply(fixture.event(agent, ModuleKind.MAIN, START, "span-main", "Choosing the next step"), null);
        AgentStatusView.Bubble bubble = board.status(agent).bubble();
        assertThat(bubble.module()).isEqualTo("Main consciousness");
        assertThat(bubble.summary()).isIn("Choosing the next step", refined);
        fixture.recorder.await("refined again for the new focus",
                () -> board.status(agent).bubble().summary().equals(refined));

        board.apply(fixture.event(agent, ModuleKind.MAIN, END, "span-main", "done"), null);
        board.apply(fixture.event(agent, ModuleKind.TOOL, END, "span-tool", "done"), null);
        assertThat(board.status(agent).bubble().summary()).isEqualTo(BubbleText.IDLE_SUMMARY);
    }

    /** v0.0.12 🍊 A blocked summarizer never slows reporting; the code text is shown until it answers. */
    @Test
    void slowSummarizerNeverBlocksReporting() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        BubbleSummarizer blocking = (agentId, events, currentDefault) -> {
            awaitQuietly(release);
            return "Preparing the launch checklist";
        };
        fixture = new BoardFixture(blocking, FAST);
        AgentStatusBoard board = fixture.board;
        AgentId agent = fixture.agent();
        long started = System.nanoTime();
        for (int i = 0; i < 500; i++) {
            board.apply(fixture.event(agent, ModuleKind.MAIN, i == 0 ? START : STATE, "span-main", "Step " + i), null);
        }
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(1_000);
        assertThat(board.status(agent).bubble().summary()).isEqualTo("Step 499");
        release.countDown();
        fixture.recorder.await("summary arrives later",
                () -> board.status(agent).bubble().summary().equals("Preparing the launch checklist"));
    }

    /** v0.0.12 🍊 A retired agent is forgotten: no more statuses, late events ignored, its room still known. */
    @Test
    void retiredAgentsAreForgotten() throws Exception {
        fixture = new BoardFixture(new CodeBubbleSummarizer(), FAST);
        AgentStatusBoard board = fixture.board;
        AgentId agent = fixture.agent();
        board.register(agent, BoardFixture.ROOM, false);
        board.apply(fixture.event(agent, ModuleKind.MAIN, START, "span-main", "Wrapping up"), null);
        fixture.recorder.await("THINKING published", () -> fixture.lastPublishedState(agent).equals("THINKING"));

        board.remove(agent);
        fixture.placements.put(agent, new AgentLookup.Placement(BoardFixture.ROOM, false, true));
        int published = fixture.statuses(agent).size();
        board.apply(fixture.event(agent, ModuleKind.MAIN, CANCELLED, "span-main", "retired"), null);
        board.setPoolSize(agent, 5);
        assertThat(board.register(agent, BoardFixture.ROOM, false)).isFalse();
        Thread.sleep(300);
        assertThat(fixture.statuses(agent)).hasSize(published);
        assertThat(board.status(agent).state()).isEqualTo("IDLE");
        assertThat(board.roomOf(agent)).isEqualTo(BoardFixture.ROOM);
    }

    /** v0.0.12 🍊 Snapshot returns statuses in order, registering present agents lazily with their pause flag. */
    @Test
    void snapshotRegistersAgentsLazily() {
        fixture = new BoardFixture(new CodeBubbleSummarizer(), FAST);
        AgentStatusBoard board = fixture.board;
        AgentId active = fixture.agent();
        AgentId paused = IdGen.newAgentId();
        fixture.placements.put(paused, new AgentLookup.Placement(BoardFixture.ROOM, true, false));
        AgentId unknown = IdGen.newAgentId();

        List<AgentStatusView> statuses = board.snapshot(
                List.of(active.value(), "not-an-agent", paused.value(), unknown.value()));
        assertThat(statuses).extracting(AgentStatusView::agentId)
                .containsExactly(active.value(), paused.value(), unknown.value());
        assertThat(statuses).extracting(AgentStatusView::state).containsExactly("IDLE", "PAUSED", "IDLE");

        board.apply(fixture.event(active, ModuleKind.PLANNING, START, "span-plan", "Updating the task list"), null);
        assertThat(board.status(active).state()).isEqualTo("THINKING");
        assertThat(board.roomOf(active)).isEqualTo(BoardFixture.ROOM);
        assertThat(board.roomOf(unknown)).as("unknown agents are never broadcast").isNull();
        assertThat(board.roomOf(AgentId.SYSTEM)).isEqualTo("*");
    }

    /** v0.0.12 🍊 Asserts state and bubble of a status. */
    private static void assertStatus(AgentStatusView status, String state, String module, String summary) {
        assertThat(status.state()).isEqualTo(state);
        assertThat(status.bubble().module()).isEqualTo(module);
        assertThat(status.bubble().summary()).isEqualTo(summary);
    }

    /** v0.0.12 🍊 Waits on a latch, ignoring interruption. */
    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** v0.0.12 🍊 Summarizer counting its calls and returning a fixed text (null keeps the code default). */
    private static final class CountingSummarizer implements BubbleSummarizer {

        private final String answer;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> lastDefault = new AtomicReference<>();

        /** v0.0.12 🍊 Creates the summarizer. */
        private CountingSummarizer(String answer) {
            this.answer = answer;
        }

        /** v0.0.12 🍊 Counts the call and returns the fixed answer. */
        @Override
        public String summarize(AgentId agentId, List<ModuleEvent> recentEvents, String currentDefault) {
            calls.incrementAndGet();
            lastDefault.set(currentDefault);
            return answer;
        }
    }
}
