import type { LlmCall, LlmCallPayload, ModuleKind } from '../types';
import { TIER_MODELS, tierOf } from './usage';
import { nowText, randInt, recordId } from './util';

/** v0.0.30 🍊 A recorded mock model call: the metadata row plus the raw payload the inspector shows. */
export interface RecordedLlmCall {
  call: LlmCall;
  payload: LlmCallPayload;
}

/** v0.0.30 🍊 Largest number of recorded calls kept in the mock (oldest dropped first). */
export const LLM_CALL_CAPACITY = 600;

/**
 * v0.0.30 🍊 Invents the model call a finished span would have made, so the Trace tab's raw-request view
 * has something realistic to show in the mock backend (`npm run build:mock`).
 */
export function recordLlmCall(
  calls: RecordedLlmCall[],
  agentId: string,
  module: ModuleKind,
  traceId: string,
  text: string,
  latencyMs: number,
  failed: boolean,
): RecordedLlmCall {
  const tier = tierOf(module);
  const model = TIER_MODELS[tier];
  const promptTokens = randInt(1500, 9000);
  const cachedTokens = Math.round(promptTokens * (0.4 + Math.random() * 0.4));
  const completionTokens = randInt(40, 400);
  const id = recordId('llm', agentId);
  const request = {
    model,
    messages: [
      { role: 'system', content: `[S0 employee handbook]\n[S1 ${module.toLowerCase()} module instructions]` },
      { role: 'user', content: `[S7 stimulus]\n${text}\n\n[S8] Current time: ${nowText()}` },
    ],
    max_completion_tokens: 2048,
    reasoning_effort: tier === 'IMPORTANT' ? 'medium' : 'low',
    prompt_cache_key: `yuzu:${module}:${agentId}`,
  };
  const response = failed
    ? { error: { message: 'The model provider timed out.', type: 'timeout' } }
    : {
        model,
        choices: [{ index: 0, message: { role: 'assistant', content: `{"reasoning":"${text}","decision":"ACT"}` }, finish_reason: 'stop' }],
        usage: {
          prompt_tokens: promptTokens,
          completion_tokens: completionTokens,
          prompt_tokens_details: { cached_tokens: cachedTokens },
        },
      };
  const recorded: RecordedLlmCall = {
    call: {
      id,
      agentId,
      module,
      tier,
      model,
      strategy: module === 'CHAT' ? 'JSON_SCHEMA_STRICT' : 'JSON_OBJECT',
      attempt: 1,
      status: failed ? 'ERROR' : 'OK',
      error: failed ? 'LlmTransportException: The model provider timed out.' : null,
      traceId,
      promptTokens: failed ? 0 : promptTokens,
      cachedTokens: failed ? 0 : cachedTokens,
      completionTokens: failed ? 0 : completionTokens,
      reasoningTokens: 0,
      estimated: false,
      latencyMs,
      ttftMs: failed ? null : randInt(120, 900),
      hasPayload: true,
      time: nowText(),
    },
    payload: { id, agentId, model, request, response },
  };
  calls.push(recorded);
  if (calls.length > LLM_CALL_CAPACITY) calls.splice(0, 100);
  return recorded;
}
