package ai.yuzu.internal;

import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.id.IdGen;
import ai.yuzu.internal.consciousness.Origin;
import ai.yuzu.internal.consciousness.PoolMessage;
import ai.yuzu.internal.memory.WorkingMemoryCompactor;
import ai.yuzu.internal.memory.WorkingMemoryService;
import ai.yuzu.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** v0.0.13 🍊 Idempotent recording, no double-storing of THINK messages, batched compaction, archive kept. */
@IntegrationTest
@Import(WorkingMemoryIntegrationTest.FakeCompactor.class)
class WorkingMemoryIntegrationTest {

    /** v0.0.13 🍊 Deterministic compactor used instead of the AI one. */
    @TestConfiguration
    static class FakeCompactor {
        static final AtomicInteger CALLS = new AtomicInteger();

        @Bean
        @Primary
        WorkingMemoryCompactor fakeWorkingMemoryCompactor() {
            return (agent, previous, entries) -> {
                CALLS.incrementAndGet();
                return (previous.isBlank() ? "" : previous + " | ") + "summary of " + entries.size() + " entries";
            };
        }
    }

    @Autowired
    private WorkingMemoryService memory;
    @Autowired
    private JdbcClient jdbc;

    /** v0.0.13 🍊 THINK loops store the self message once; duplicates are ignored. */
    @Test
    void recordingIsIdempotentAndSelfThoughtsAreStoredOnce() {
        AgentId agent = IdGen.newAgentId();
        PoolMessage external = message(agent, "pool-" + agent.hex() + "-0000000001", Origin.EXTERNAL, false);
        memory.recordInputs(agent, "run-1", List.of(external));
        memory.recordInputs(agent, "run-1", List.of(external));
        memory.recordOutput(agent, "run-1", "THINK: I should check the budget first.");
        PoolMessage self = message(agent, "pool-" + agent.hex() + "-0000000002", Origin.SELF, true);
        memory.recordInputs(agent, "run-2", List.of(self));
        assertThat(memory.verbatimCount(agent)).isEqualTo(2);
        assertThat(memory.render(agent)).contains("IN from Alice (human) told me").contains("OUT me (my decision)");
    }

    /** v0.0.13 🍊 At 20 verbatim entries the oldest are folded into the digest and 10 remain; rows are kept. */
    @Test
    void compactsAtTwentyKeepingTen() {
        AgentId agent = IdGen.newAgentId();
        List<PoolMessage> messages = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            messages.add(message(agent, String.format("pool-%s-%010x", agent.hex(), i), Origin.EXTERNAL, false));
        }
        for (PoolMessage m : messages.subList(0, 19)) {
            memory.recordInputs(agent, "run-" + m.id(), List.of(m));
        }
        assertThat(memory.verbatimCount(agent)).isEqualTo(19);
        memory.recordInputs(agent, "run-20", List.of(messages.get(19)));
        awaitDigest(agent);
        assertThat(memory.verbatimCount(agent)).isEqualTo(WorkingMemoryService.KEEP_VERBATIM);
        for (PoolMessage m : messages.subList(20, 25)) {
            memory.recordInputs(agent, "run-" + m.id(), List.of(m));
        }
        assertThat(memory.view(agent).digest()).startsWith("summary of");
        long total = jdbc.sql("SELECT COUNT(*) FROM working_memory_entry WHERE agent_id = :a")
                .param("a", agent.value()).query(Long.class).single();
        assertThat(total).isEqualTo(25);
    }

    private void awaitDigest(AgentId agent) {
        for (int i = 0; i < 250 && memory.view(agent).digest() == null; i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static PoolMessage message(AgentId agent, String id, Origin origin, boolean emittedByMain) {
        return new PoolMessage(id, agent, origin, "Alice (human) told me in the group chat at Sat Sep 19, 11:32:05 AM",
                "Please prepare the weekly report " + id, null, 0, emittedByMain, Instant.now());
    }
}
