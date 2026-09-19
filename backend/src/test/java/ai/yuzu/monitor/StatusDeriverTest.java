package ai.yuzu.monitor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.12 🍊 Pure derivation rules: state priorities, focus choice, active modules and bubbles. */
class StatusDeriverTest {

    /** v0.0.12 🍊 ERROR > PAUSED > WAITING > the focus span's desk state > IDLE. */
    @Test
    void statePriorities() {
        ActiveSpan main = span(ModuleKind.MAIN, DeskState.THINKING, 1);
        assertThat(StatusDeriver.state(true, true, true, main)).isEqualTo(DeskState.ERROR);
        assertThat(StatusDeriver.state(false, true, true, main)).isEqualTo(DeskState.PAUSED);
        assertThat(StatusDeriver.state(false, false, true, main)).isEqualTo(DeskState.WAITING);
        assertThat(StatusDeriver.state(false, false, false, main)).isEqualTo(DeskState.THINKING);
        assertThat(StatusDeriver.state(false, false, false, null)).isEqualTo(DeskState.IDLE);
        assertThat(DeskState.ERROR.priority()).isGreaterThan(DeskState.PAUSED.priority());
        assertThat(DeskState.PAUSED.priority()).isGreaterThan(DeskState.WAITING.priority());
        assertThat(DeskState.WAITING.priority()).isGreaterThan(DeskState.TALKING.priority());
        assertThat(DeskState.TALKING.priority()).isGreaterThan(DeskState.THINKING.priority());
        assertThat(DeskState.THINKING.priority()).isGreaterThan(DeskState.WORKING.priority());
        assertThat(DeskState.WORKING.priority()).isGreaterThan(DeskState.IDLE.priority());
    }

    /** v0.0.12 🍊 The focus is the highest desk state, then the most relevant module, then the most recent span. */
    @Test
    void focusChoice() {
        ActiveSpan tool = span(ModuleKind.TOOL, DeskState.WORKING, 3);
        ActiveSpan main = span(ModuleKind.MAIN, DeskState.THINKING, 1);
        ActiveSpan chat = span(ModuleKind.CHAT, DeskState.TALKING, 2);
        assertThat(StatusDeriver.focus(List.of(tool, main, chat))).isEqualTo(chat);
        assertThat(StatusDeriver.focus(List.of(tool, main))).isEqualTo(main);
        assertThat(StatusDeriver.focus(List.of(main, span(ModuleKind.PLANNING, DeskState.THINKING, 5)))).isEqualTo(main);
        ActiveSpan older = span(ModuleKind.SUBCONSCIOUS, DeskState.THINKING, 7);
        ActiveSpan newer = span(ModuleKind.SUBCONSCIOUS, DeskState.THINKING, 8);
        assertThat(StatusDeriver.focus(List.of(newer, older))).isEqualTo(newer);
        assertThat(StatusDeriver.focus(List.of())).isNull();
    }

    /** v0.0.12 🍊 Active modules are distinct and ordered by relevance. */
    @Test
    void activeModulesAreDistinctAndOrdered() {
        List<ActiveSpan> spans = List.of(span(ModuleKind.SUBCONSCIOUS, DeskState.THINKING, 1),
                span(ModuleKind.TOOL, DeskState.WORKING, 2), span(ModuleKind.SUBCONSCIOUS, DeskState.THINKING, 3),
                span(ModuleKind.MAIN, DeskState.THINKING, 4));
        assertThat(StatusDeriver.activeModules(spans)).containsExactly("MAIN", "SUBCONSCIOUS", "TOOL");
    }

    /** v0.0.12 🍊 Each state gets its bubble: failure, pause, waiting reason, focus span, idle. */
    @Test
    void bubbles() {
        ActiveSpan chat = span(ModuleKind.CHAT, DeskState.TALKING, 1);
        assertThat(StatusDeriver.bubble(DeskState.ERROR, ModuleKind.TOOL, "Failed: timeout", false, null, chat, "x"))
                .isEqualTo(new AgentStatusView.Bubble("Tool", "Failed: timeout"));
        assertThat(StatusDeriver.bubble(DeskState.PAUSED, null, null, false, null, chat, "x"))
                .isEqualTo(new AgentStatusView.Bubble(BubbleText.PAUSED_MODULE, BubbleText.PAUSED_SUMMARY));
        assertThat(StatusDeriver.bubble(DeskState.IDLE, null, null, false, null, null, null))
                .isEqualTo(new AgentStatusView.Bubble(BubbleText.IDLE_MODULE, BubbleText.IDLE_SUMMARY));
        assertThat(StatusDeriver.bubble(DeskState.WAITING, null, null, true, "Approve the $4,200 trade?", chat, "x"))
                .isEqualTo(new AgentStatusView.Bubble(BubbleText.WAITING_MODULE, "Approve the $4,200 trade?"));
        assertThat(StatusDeriver.bubble(DeskState.WAITING, null, null, true, null, null, null))
                .isEqualTo(new AgentStatusView.Bubble(BubbleText.WAITING_MODULE, BubbleText.WAITING_SUMMARY));
        ActiveSpan asking = span(ModuleKind.TOOL, DeskState.WAITING, 2);
        assertThat(StatusDeriver.bubble(DeskState.WAITING, null, null, false, null, asking, "Asking Alice"))
                .isEqualTo(new AgentStatusView.Bubble("Tool", "Asking Alice"));
        assertThat(StatusDeriver.bubble(DeskState.TALKING, null, null, false, null, chat, "Replying to Bob"))
                .isEqualTo(new AgentStatusView.Bubble("Chat", "Replying to Bob"));
    }

    /** v0.0.12 🍊 A running span for the tests. */
    private static ActiveSpan span(ModuleKind module, DeskState desk, long tick) {
        return new ActiveSpan("span-" + tick, module, desk, module.label() + " at work", tick);
    }
}
