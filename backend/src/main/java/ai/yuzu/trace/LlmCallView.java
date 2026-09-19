package ai.yuzu.trace;

import ai.yuzu.llm.usage.Usage;

/**
 * v0.0.30 🍊 One recorded HTTP attempt against the model provider (contract type {@code LlmCall}).
 *
 * <p>Metadata only: the full request and response JSON of a call with {@code hasPayload} is fetched on demand
 * from {@code GET /api/llm-calls/{callId}/payload}, because a single prompt can be hundreds of kilobytes.</p>
 *
 * @param module     reporting module, matching {@code ModuleEvent.module} of the span that made the call
 * @param strategy   JSON strategy used (JSON_SCHEMA_STRICT, JSON_OBJECT, PROMPT_ONLY or TEXT)
 * @param attempt    1 for the first HTTP attempt of the logical call, 2+ for retries
 * @param status     OK or ERROR
 * @param hasPayload true when the raw request/response JSON was written to the agent's workspace
 */
public record LlmCallView(String id, String agentId, String module, String tier, String model, String strategy,
                          int attempt, String status, String error, String traceId, int promptTokens,
                          int cachedTokens, int completionTokens, int reasoningTokens, boolean estimated,
                          long latencyMs, Long ttftMs, boolean hasPayload, String time) {

    /** v0.0.30 🍊 Builds the view from a row's usage block. */
    public static LlmCallView of(String id, String agentId, String module, String tier, String model, String strategy,
                                 int attempt, String status, String error, String traceId, Usage usage, long latencyMs,
                                 Long ttftMs, boolean hasPayload, String time) {
        return new LlmCallView(id, agentId, module, tier, model, strategy, attempt, status, error, traceId,
                usage.promptTokens(), usage.cachedTokens(), usage.completionTokens(), usage.reasoningTokens(),
                usage.estimated(), latencyMs, ttftMs, hasPayload, time);
    }
}
