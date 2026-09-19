package ai.yuzu.monitor;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.12 🍊 Enumerations match the frozen contract; module labels and implied desk states follow the spec. */
class ModuleKindTest {

    /** v0.0.12 🍊 Names (and order) of ModuleKind, DeskState and EventPhase equal frontend/src/api/types.ts. */
    @Test
    void namesMatchTheContract() {
        assertThat(Arrays.stream(ModuleKind.values()).map(Enum::name)).containsExactly("CHAT", "SAFETY", "BEHAVIOR",
                "HIGH_RISK", "TOOL_CALLING", "MONITOR", "MAIN", "PLANNING", "COGNITION", "SUBCONSCIOUS", "LEARNING",
                "MEMORY", "WM_COMPACTOR", "TOOL", "SYSTEM");
        assertThat(Arrays.stream(DeskState.values()).map(Enum::name))
                .containsExactly("IDLE", "WORKING", "THINKING", "TALKING", "WAITING", "PAUSED", "ERROR");
        assertThat(Arrays.stream(EventPhase.values()).map(Enum::name))
                .containsExactly("START", "STATE", "END", "ERROR", "CANCELLED", "INFO");
    }

    /** v0.0.12 🍊 Thinking modules imply THINKING, tools and reviews WORKING; only the monitor itself is quiet. */
    @Test
    void labelsAndImpliedDeskStates() {
        assertThat(ModuleKind.CHAT.label()).isEqualTo("Chat");
        assertThat(ModuleKind.SAFETY.label()).isEqualTo("Safety review");
        assertThat(ModuleKind.MAIN.label()).isEqualTo("Main consciousness");
        for (ModuleKind module : List.of(ModuleKind.MAIN, ModuleKind.SUBCONSCIOUS, ModuleKind.PLANNING,
                ModuleKind.COGNITION)) {
            assertThat(module.desk()).as(module.name()).isEqualTo(DeskState.THINKING);
        }
        for (ModuleKind module : List.of(ModuleKind.TOOL, ModuleKind.TOOL_CALLING, ModuleKind.SAFETY,
                ModuleKind.BEHAVIOR, ModuleKind.HIGH_RISK, ModuleKind.CHAT)) {
            assertThat(module.desk()).as(module.name()).isEqualTo(DeskState.WORKING);
        }
        for (ModuleKind module : ModuleKind.values()) {
            assertThat(module.label()).isNotBlank();
            assertThat(module.quiet()).as(module.name()).isEqualTo(module == ModuleKind.MONITOR);
        }
        assertThat(DeskState.PAUSED.declarable()).isFalse();
        assertThat(DeskState.ERROR.declarable()).isFalse();
        assertThat(DeskState.TALKING.declarable()).isTrue();
        assertThat(EventPhase.END.terminal()).isTrue();
        assertThat(EventPhase.STATE.terminal()).isFalse();
    }
}
