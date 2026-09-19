package ai.yuzu.monitor;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.IdGen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.12 🍊 Lifecycle bridge: at startup every present agent is tracked with its room and pause flag. */
class MonitorAgentListenerTest {

    private final BoardFixture fixture = new BoardFixture(new CodeBubbleSummarizer(), MonitorTimings.DEFAULTS);

    /** v0.0.12 🍊 Stops the fixture. */
    @AfterEach
    void tearDown() {
        fixture.close();
    }

    /** v0.0.12 🍊 The startup preload registers present agents (no lazy lookup needed) and publishes their statuses. */
    @Test
    void presentAgentsAreTrackedAtStartup() throws Exception {
        AgentId active = IdGen.newAgentId();
        AgentId paused = IdGen.newAgentId();
        Map<AgentId, AgentLookup.Placement> present = new LinkedHashMap<>();
        present.put(active, new AgentLookup.Placement(BoardFixture.ROOM, false, false));
        present.put(paused, new AgentLookup.Placement(BoardFixture.ROOM, true, false));
        AgentLookup registry = new AgentLookup() {
            /** v0.0.12 🍊 Lazy lookups find nothing, so only the preload can register the agents. */
            @Override
            public Optional<Placement> find(AgentId agentId) {
                return Optional.empty();
            }

            /** v0.0.12 🍊 The agents present at startup. */
            @Override
            public Map<AgentId, Placement> presentAgents() {
                return present;
            }
        };

        new MonitorAgentListener(fixture.board, MonitorService.noop(), registry).registerPresentAgents();

        assertThat(fixture.board.status(active).state()).isEqualTo("IDLE");
        assertThat(fixture.board.status(paused).state()).isEqualTo("PAUSED");
        assertThat(fixture.board.roomOf(active)).isEqualTo(BoardFixture.ROOM);
        fixture.recorder.await("initial statuses published",
                () -> !fixture.statuses(active).isEmpty() && !fixture.statuses(paused).isEmpty());
    }
}
