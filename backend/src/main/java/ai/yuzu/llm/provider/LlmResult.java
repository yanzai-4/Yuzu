package ai.yuzu.llm.provider;

import ai.yuzu.llm.usage.Usage;

/**
 * v0.0.8 🍊 Outcome of one provider call.
 *
 * @param text         assistant text (empty string when the model returned nothing)
 * @param finishReason "stop", "length", "content_filter", ... (may be null)
 * @param refusal      refusal message when the model refused (structured outputs), else null
 * @param latencyMs    wall time of the HTTP exchange
 * @param ttftMs       time to first token when streaming, else null
 * @param requestJson  exact JSON body that was sent (for the trace inspector; contains no key)
 * @param responseJson raw response body (streaming: reconstructed summary)
 */
public record LlmResult(String text, String finishReason, String refusal, Usage usage, String model, long latencyMs,
                        Long ttftMs, String requestJson, String responseJson) {
}
