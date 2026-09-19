package ai.yuzu.perf;

import ai.yuzu.agent.AgentProfile;
import ai.yuzu.agent.AgentService;
import ai.yuzu.agent.CreateAgentRequest;
import ai.yuzu.agent.Permission;
import ai.yuzu.agent.Role;
import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.agent.runtime.AgentRuntimeManager;
import ai.yuzu.common.error.ToolExecutionException;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.external.toolcall.ToolDispatcher;
import ai.yuzu.external.toolcall.ToolPlan;
import ai.yuzu.support.IntegrationTest;
import ai.yuzu.tool.spi.PermissionGuard;
import ai.yuzu.tool.spi.Risk;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolContext;
import ai.yuzu.tool.spi.ToolResult;
import ai.yuzu.tool.spi.ToolSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v0.0.31 🍊 Every failure branch of the tool dispatcher: nothing aborts a batch and nothing is left RUNNING.
 *
 * <p>Covers the timeout (the worker is interrupted and the batch continues), a tool that throws, a tool that
 * returns nothing, an unknown tool, invalid arguments, a permission denied in code, and a mixed batch where
 * one call fails and the others still succeed. Each branch must also leave a finished {@code tool_call} row
 * behind, because the agent reads its own history from there.</p>
 */
@IntegrationTest
@Import(ToolFailurePathTest.FailingTools.class)
class ToolFailurePathTest {

    /** v0.0.31 🍊 Test-only tools, one per failure branch. */
    @TestConfiguration
    static class FailingTools {

        static final AtomicBoolean SLOW_TOOL_INTERRUPTED = new AtomicBoolean();

        /** v0.0.31 🍊 A tool that outlives its 200 ms timeout. */
        @Bean
        Tool<NoArgs> testSlowTool() {
            return new SimpleTool("test_slow", "Never finishes in time", Duration.ofMillis(200), Set.of(),
                    ctx -> {
                        try {
                            Thread.sleep(30_000);
                        } catch (InterruptedException e) {
                            SLOW_TOOL_INTERRUPTED.set(true);
                            Thread.currentThread().interrupt();
                        }
                        return ToolResult.ok("late", Instant.now());
                    });
        }

        /** v0.0.31 🍊 A tool that throws an unexpected runtime exception. */
        @Bean
        Tool<NoArgs> testBoomTool() {
            return new SimpleTool("test_boom", "Always explodes", Duration.ofSeconds(5), Set.of(), ctx -> {
                throw new IllegalStateException("kaboom");
            });
        }

        /** v0.0.31 🍊 A tool that throws a typed Yuzu failure (the expected way to report a tool problem). */
        @Bean
        Tool<NoArgs> testTypedFailureTool() {
            return new SimpleTool("test_typed", "Fails with a typed error", Duration.ofSeconds(5), Set.of(),
                    ctx -> {
                        throw new ToolExecutionException("the fake broker is down");
                    });
        }

        /** v0.0.31 🍊 A tool that returns nothing at all. */
        @Bean
        Tool<NoArgs> testNullTool() {
            return new SimpleTool("test_null", "Returns nothing", Duration.ofSeconds(5), Set.of(), ctx -> null);
        }

        /** v0.0.31 🍊 A tool that needs a permission a researcher does not have. */
        @Bean
        Tool<NoArgs> testPrivilegedTool() {
            return new SimpleTool("test_privileged", "Needs TRADE_EXECUTE", Duration.ofSeconds(5),
                    Set.of(Permission.TRADE_EXECUTE), ctx -> ToolResult.ok("traded", Instant.now()));
        }

        /** v0.0.31 🍊 A tool that always works (used to prove a failing sibling does not abort the batch). */
        @Bean
        Tool<NoArgs> testFineTool() {
            return new SimpleTool("test_fine", "Always works", Duration.ofSeconds(5), Set.of(),
                    ctx -> ToolResult.ok("fine", Instant.now()));
        }
    }

    /** v0.0.31 🍊 Argument record shared by the test tools (no arguments). */
    public record NoArgs(String note) {
    }

    /** v0.0.31 🍊 Minimal tool built from a name, a timeout, required permissions and a body. */
    record SimpleTool(String name, String description, Duration timeout, Set<Permission> permissions,
                      java.util.function.Function<ToolContext, ToolResult> body) implements Tool<NoArgs> {

        /** v0.0.31 🍊 Static description. */
        @Override
        public ToolSpec<NoArgs> spec() {
            return new ToolSpec<>(name, description, NoArgs.class, permissions, Risk.LOW, timeout, true,
                    "I ran a test tool");
        }

        /** v0.0.31 🍊 Runs the body. */
        @Override
        public ToolResult execute(ToolContext ctx, NoArgs args) {
            return body.apply(ctx);
        }
    }

    @Autowired
    private ToolDispatcher dispatcher;
    @Autowired
    private AgentService agents;
    @Autowired
    private AgentRuntimeManager runtimes;
    @Autowired
    private NaturalTime time;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private PermissionGuard guard;

    private AgentProfile lime;
    private String batchId;

    @BeforeEach
    void setUp() {
        FailingTools.SLOW_TOOL_INTERRUPTED.set(false);
        String roomId = String.format("room-%04x", ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        jdbc.sql("INSERT INTO room (id, name, created_at) VALUES (:id, 'Tool room', UTC_TIMESTAMP(3))")
                .param("id", roomId).update();
        lime = agents.createNamed(roomId, new CreateAgentRequest(Role.RESEARCHER, null, null, null, null, null),
                "Lime");
        batchId = "batch-test-" + Integer.toHexString(ThreadLocalRandom.current().nextInt());
        assertThat(guard).isNotNull();
    }

    /** v0.0.31 🍊 A tool that overruns its timeout is interrupted and reported, and the batch keeps going. */
    @Test
    void timeoutIsContainedAndInterruptsTheWorker() throws Exception {
        Map<String, ToolResult> results = dispatch(List.of("wait forever", "do something easy"),
                call(0, "test_slow"), call(1, "test_fine"));
        assertThat(results.get("test_slow").status()).isEqualTo(ToolResult.Status.ERROR);
        assertThat(results.get("test_slow").output()).contains("timed out after");
        assertThat(results.get("test_fine").status()).isEqualTo(ToolResult.Status.OK);
        for (int i = 0; i < 200 && !FailingTools.SLOW_TOOL_INTERRUPTED.get(); i++) {
            Thread.sleep(10);
        }
        assertThat(FailingTools.SLOW_TOOL_INTERRUPTED).isTrue();
        assertStatus("test_slow", "ERROR");
    }

    /** v0.0.31 🍊 An unexpected exception becomes an ERROR result naming only the exception type. */
    @Test
    void unexpectedExceptionsBecomeErrorResults() {
        Map<String, ToolResult> results = dispatch(List.of("explode"), call(0, "test_boom"));
        assertThat(results.get("test_boom").status()).isEqualTo(ToolResult.Status.ERROR);
        assertThat(results.get("test_boom").output()).isEqualTo("The tool failed: IllegalStateException");
        assertThat(results.get("test_boom").output()).doesNotContain("kaboom");
        assertStatus("test_boom", "ERROR");
    }

    /** v0.0.31 🍊 A typed tool failure keeps its message, which the agent can act on. */
    @Test
    void typedFailuresKeepTheirMessage() {
        Map<String, ToolResult> results = dispatch(List.of("trade"), call(0, "test_typed"));
        assertThat(results.get("test_typed").status()).isEqualTo(ToolResult.Status.ERROR);
        assertThat(results.get("test_typed").output()).contains("the fake broker is down");
    }

    /** v0.0.31 🍊 A tool that returns null does not produce a null result downstream. */
    @Test
    void nullResultsAreReplaced() {
        Map<String, ToolResult> results = dispatch(List.of("nothing"), call(0, "test_null"));
        assertThat(results.get("test_null").output()).isEqualTo("The tool returned nothing.");
        assertThat(results.get("test_null").status()).isEqualTo(ToolResult.Status.ERROR);
    }

    /** v0.0.31 🍊 A hallucinated tool name and invalid arguments both fail cleanly, never with an exception. */
    @Test
    void unknownToolsAndBadArgumentsAreRejected() {
        Map<String, ToolResult> results = dispatch(List.of("use magic", "call with junk"),
                call(0, "test_teleport"), new ToolPlan.Call(1, "test_fine", "{\"note\":42}"));
        assertThat(results.get("test_teleport").output()).isEqualTo("There is no tool named test_teleport.");
        assertThat(results.get("test_teleport").status()).isEqualTo(ToolResult.Status.ERROR);
        assertThat(results.get("test_fine").status()).isEqualTo(ToolResult.Status.ERROR);
        assertThat(results.get("test_fine").output()).startsWith("Invalid arguments:");
    }

    /** v0.0.31 🍊 A tool the agent may not use is DENIED by code, not by the model. */
    @Test
    void missingPermissionIsDeniedInCode() {
        Map<String, ToolResult> results = dispatch(List.of("place a trade"), call(0, "test_privileged"));
        assertThat(results.get("test_privileged").status()).isEqualTo(ToolResult.Status.DENIED);
        assertThat(results.get("test_privileged").output()).contains("not allowed to use test_privileged");
        assertStatus("test_privileged", "DENIED");
    }

    /** v0.0.31 🍊 Every branch in one batch: the batch completes and no row is left RUNNING. */
    @Test
    void oneBatchWithEveryFailureStillCompletes() {
        Map<String, ToolResult> results = dispatch(
                List.of("explode", "nothing", "use magic", "place a trade", "do something easy"),
                call(0, "test_boom"), call(1, "test_null"), call(2, "test_teleport"),
                call(3, "test_privileged"), call(4, "test_fine"));
        assertThat(results).hasSize(5);
        assertThat(results.get("test_fine").status()).isEqualTo(ToolResult.Status.OK);
        Long running = jdbc.sql("SELECT COUNT(*) FROM tool_call WHERE agent_id = :a AND status = 'RUNNING'")
                .param("a", lime.agentId().value()).query(Long.class).single();
        assertThat(running).isZero();
        Long rows = jdbc.sql("SELECT COUNT(*) FROM tool_call WHERE agent_id = :a AND batch_id = :b")
                .param("a", lime.agentId().value()).param("b", batchId).query(Long.class).single();
        assertThat(rows).isEqualTo(5);
    }

    /** v0.0.31 🍊 Dispatches a plan and maps the outcomes by tool name. */
    private Map<String, ToolResult> dispatch(List<String> actions, ToolPlan.Call... calls) {
        AgentContext ctx = runtimes.require(lime.agentId()).context(null, null, time);
        ToolPlan plan = new ToolPlan("test", List.of(calls), List.of());
        return dispatcher.dispatch(ctx, batchId, actions, plan, 0).stream()
                .collect(java.util.stream.Collectors.toMap(o -> o.call().tool(), ToolDispatcher.Outcome::result));
    }

    /** v0.0.31 🍊 A call with empty arguments. */
    private static ToolPlan.Call call(int index, String tool) {
        return new ToolPlan.Call(index, tool, "{\"note\":\"x\"}");
    }

    /** v0.0.31 🍊 Asserts the persisted status of a tool call. */
    private void assertStatus(String tool, String status) {
        String stored = jdbc.sql("SELECT status FROM tool_call WHERE agent_id = :a AND batch_id = :b AND tool = :t")
                .param("a", lime.agentId().value()).param("b", batchId).param("t", tool)
                .query(String.class).single();
        assertThat(stored).isEqualTo(status);
    }
}
