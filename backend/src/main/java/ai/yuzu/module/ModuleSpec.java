package ai.yuzu.module;

import ai.yuzu.llm.ModelTier;

/**
 * v0.0.14 🍊 Static description of an AI module.
 *
 * @param module     module name (monitor, gating, metering; for example "CHAT")
 * @param label      human label shown in the desk bubble ("Chat triage")
 * @param tier       model tier from the console
 * @param template   prompt template name under prompts/modules/
 * @param outputType output record type (strict JSON schema)
 * @param cacheable  true only when the answer is a pure function of the prompt (local response cache)
 */
public record ModuleSpec<O>(String module, String label, ModelTier tier, String template, Class<O> outputType,
                            boolean cacheable) {
}
