package ai.yuzu.external.toolcall;

import ai.yuzu.agent.runtime.AgentContext;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.external.behavior.BehaviorReviewModule;
import ai.yuzu.llm.ModelTier;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.prompt.SegmentRank;
import ai.yuzu.module.AiModule;
import ai.yuzu.module.ModuleDeps;
import ai.yuzu.module.ModuleSpec;
import ai.yuzu.tool.spi.Tool;
import ai.yuzu.tool.spi.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * v0.0.18 🍊 Tool calling (DEFAULT tier): decomposes natural-language actions into concrete tool calls.
 *
 * <p>Sees the actions, the agent's profile and permission scope, and the full tool catalog (static, in S1).
 * Code validates every call: the tool must exist and the arguments must match its schema; every action must be
 * covered by a call or listed as infeasible.</p>
 */
@Component
public class ToolCallingModule extends AiModule<List<String>, ToolPlan> {

    private static final ModuleSpec<ToolPlan> SPEC = new ModuleSpec<>("TOOL_CALLING", "Planning tool calls",
            ModelTier.DEFAULT, "tool_calling", ToolPlan.class, false);

    private final ToolRegistry registry;
    private final ObjectMapper mapper;

    /** v0.0.18 🍊 Injects dependencies. */
    public ToolCallingModule(ModuleDeps deps, ToolRegistry registry, ObjectMapper mapper) {
        super(deps);
        this.registry = registry;
        this.mapper = mapper;
    }

    /** v0.0.18 🍊 Static description. */
    @Override
    protected ModuleSpec<ToolPlan> spec() {
        return SPEC;
    }

    /** v0.0.18 🍊 The full tool catalog is static text (appended to S1 for caching). */
    @Override
    protected String staticInstructions() {
        return "## Tool catalog\n" + registry.allTools();
    }

    /** v0.0.18 🍊 S3 profile + scope, S7 actions. */
    @Override
    protected void compose(AgentContext ctx, List<String> actions, PromptBuilder prompt) {
        prompt.add(SegmentRank.S3_SELF, ctx.profile().describe())
                .add(SegmentRank.S7_STIMULUS, "Actions", BehaviorReviewModule.numbered(actions));
    }

    /** v0.0.18 🍊 Tools must exist, arguments must validate, every action must be covered. */
    @Override
    protected List<String> semanticErrors(AgentContext ctx, List<String> actions, ToolPlan output) {
        List<String> errors = new ArrayList<>();
        Set<Integer> covered = new HashSet<>();
        for (ToolPlan.Call call : output.calls()) {
            if (call.actionIndex() < 0 || call.actionIndex() >= actions.size()) {
                errors.add("call for unknown actionIndex " + call.actionIndex() + ".");
                continue;
            }
            covered.add(call.actionIndex());
            Optional<Tool<?>> tool = registry.find(call.tool());
            if (tool.isEmpty()) {
                errors.add("tool '" + call.tool() + "' does not exist; use only tools from the catalog.");
                continue;
            }
            try {
                JsonNode args = mapper.readTree(call.argsJson());
                registry.validateArgs(tool.get(), args)
                        .forEach(e -> errors.add(call.tool() + " arguments: " + e));
            } catch (Exception e) {
                errors.add(call.tool() + " argsJson is not a valid JSON object.");
            }
        }
        output.infeasible().forEach(i -> covered.add(i.actionIndex()));
        for (int i = 0; i < actions.size(); i++) {
            if (!covered.contains(i)) {
                errors.add("action " + i + " has no call and is not listed as infeasible.");
            }
        }
        return errors;
    }

    /** v0.0.18 🍊 When planning tool calls keeps failing, every action is reported as infeasible. */
    @Override
    protected Optional<ToolPlan> degrade(AgentContext ctx, List<String> actions, YuzuException error) {
        List<ToolPlan.Infeasible> all = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            all.add(new ToolPlan.Infeasible(i, "tool planning failed (" + error.code() + ")"));
        }
        return Optional.of(new ToolPlan("fallback", List.of(), all));
    }

    /** v0.0.18 🍊 Monitor text. */
    @Override
    protected String endText(ToolPlan output) {
        return output.calls().size() + " tool call(s) planned";
    }
}
