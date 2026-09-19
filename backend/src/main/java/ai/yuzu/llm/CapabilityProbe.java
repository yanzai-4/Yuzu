package ai.yuzu.llm;

import ai.yuzu.common.concurrent.CancelToken;
import ai.yuzu.common.error.YuzuException;
import ai.yuzu.common.id.AgentId;
import ai.yuzu.common.time.NaturalTime;
import ai.yuzu.llm.prompt.PromptBuilder;
import ai.yuzu.llm.prompt.PromptLibrary;
import ai.yuzu.llm.prompt.SegmentRank;
import ai.yuzu.llm.structured.SchemaInstructions;
import ai.yuzu.llm.structured.SemanticCheck;
import ai.yuzu.llm.structured.StrictSchemaFactory;
import ai.yuzu.llm.structured.StructuredResult;
import ai.yuzu.settings.SettingsService;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * v0.0.11 🍊 The console's "Test" button: one tiny structured call per tier.
 *
 * <p>Discovers (and remembers) which JSON strategy each tier's model supports, measures latency, and warms
 * the provider's prompt cache for the shared handbook prefix.</p>
 */
@Component
public class CapabilityProbe {

    /** v0.0.11 🍊 Expected probe answer. */
    record ProbeAnswer(boolean ok, String echo) {
    }

    /** v0.0.11 🍊 Result of one tier (contract type {@code LlmTestResult.tiers[tier]}). */
    public record TierResult(boolean ok, String model, String strategy, Long latencyMs, String error) {
    }

    /** v0.0.11 🍊 Result of the whole test (contract type {@code LlmTestResult}). */
    public record TestResult(Map<ModelTier, TierResult> tiers) {
    }

    private final LlmGateway gateway;
    private final SettingsService settings;
    private final PromptLibrary library;
    private final StrictSchemaFactory schemas;
    private final NaturalTime time;

    /** v0.0.11 🍊 Injects collaborators. */
    public CapabilityProbe(LlmGateway gateway, SettingsService settings, PromptLibrary library,
                           StrictSchemaFactory schemas, NaturalTime time) {
        this.gateway = gateway;
        this.settings = settings;
        this.library = library;
        this.schemas = schemas;
        this.time = time;
    }

    /** v0.0.11 🍊 Probes every tier; failures are reported per tier instead of thrown. */
    public TestResult test() {
        Map<ModelTier, TierResult> results = new EnumMap<>(ModelTier.class);
        for (ModelTier tier : ModelTier.values()) {
            String model = settings.current().tier(tier).model();
            long started = System.nanoTime();
            try {
                StructuredResult<ProbeAnswer> answer = gateway.structured(
                        new LlmCallContext(AgentId.SYSTEM, "SYSTEM", tier, null, new CancelToken()),
                        ProbeAnswer.class,
                        strategy -> PromptBuilder.start(library.handbook(),
                                        "Connectivity check. Reply with ok = true and echo = \"citrus\".\n"
                                                + SchemaInstructions.forStrategy(strategy,
                                                schemas.schemaText(ProbeAnswer.class)))
                                .add(SegmentRank.S7_STIMULUS, "Probe from the Yuzu console.")
                                .build(time.now()),
                        answer1 -> answer1.ok() ? List.of() : List.of("Set ok to true."), false);
                results.put(tier, new TierResult(true, model, answer.strategy().name(),
                        (System.nanoTime() - started) / 1_000_000, null));
            } catch (YuzuException e) {
                results.put(tier, new TierResult(false, model, null, null, e.code() + ": " + e.getMessage()));
            }
        }
        return new TestResult(results);
    }
}
