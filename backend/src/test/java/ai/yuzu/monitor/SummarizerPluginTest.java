package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.12 🍊 The bubble summarizer plug-in point: a @Primary bean replaces the code default; ambiguity falls back safely. */
class SummarizerPluginTest {

    private static final String REFINED = "Refined by the LIGHT model";

    private final BoardFixture fixture = new BoardFixture(new CodeBubbleSummarizer(), MonitorTimings.DEFAULTS);

    /** v0.0.12 🍊 Stops the fixture. */
    @AfterEach
    void tearDown() {
        fixture.close();
    }

    /** v0.0.12 🍊 With the code summarizer and a primary LIGHT-AI summarizer registered, the primary one is used. */
    @Test
    void primarySummarizerBeanWins() throws Exception {
        try (GenericApplicationContext beans = summarizers(true)) {
            AgentStatusBoard board = board(beans);
            AgentId agent = fixture.agent();
            board.apply(fixture.event(agent, ModuleKind.MAIN, EventPhase.START, "span-main", "Reading the brief"),
                    null);
            fixture.recorder.await("primary summary shown",
                    () -> board.status(agent).bubble().summary().equals(REFINED));
        }
    }

    /** v0.0.12 🍊 Two summarizers without a primary one: the board keeps working with the code summary. */
    @Test
    void ambiguousSummarizersFallBackToCode() throws Exception {
        try (GenericApplicationContext beans = summarizers(false)) {
            AgentStatusBoard board = board(beans);
            AgentId agent = fixture.agent();
            board.apply(fixture.event(agent, ModuleKind.MAIN, EventPhase.START, "span-main", "Reading the brief"),
                    null);
            Thread.sleep(300);
            assertThat(board.status(agent).bubble().summary()).isEqualTo("Reading the brief");
            assertThat(fixture.asyncFailures).isEmpty();
        }
    }

    /** v0.0.12 🍊 A context holding the code summarizer plus a replacement, primary or not (as a LIGHT-AI module would add it). */
    private static GenericApplicationContext summarizers(boolean primary) {
        GenericApplicationContext beans = new GenericApplicationContext();
        beans.registerBean("codeBubbleSummarizer", CodeBubbleSummarizer.class, CodeBubbleSummarizer::new);
        beans.registerBean("lightSummarizer", BubbleSummarizer.class,
                () -> (agentId, recentEvents, currentDefault) -> REFINED,
                definition -> definition.setPrimary(primary));
        beans.refresh();
        return beans;
    }

    /** v0.0.12 🍊 A board built through its Spring constructor over the given beans. */
    private AgentStatusBoard board(GenericApplicationContext beans) {
        return new AgentStatusBoard(id -> Optional.ofNullable(fixture.placements.get(id)), fixture.hub, fixture.time,
                fixture.runner, fixture.timer, beans.getBeanProvider(BubbleSummarizer.class));
    }
}
