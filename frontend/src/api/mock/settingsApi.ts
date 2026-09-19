import type { YuzuApi } from '../client';
import type { LlmTestResult, Tier } from '../types';
import type { MockContext } from './context';
import { clone, randInt, sleep } from './util';

const TIERS: Tier[] = ['IMPORTANT', 'DEFAULT', 'LIGHT'];
const PROVIDERS = ['OPENAI', 'EDGEONE', 'CUSTOM'];
const MODELS: Record<string, string[]> = {
  OPENAI: ['gpt-5', 'gpt-5-mini', 'gpt-5-nano', 'gpt-4.1', 'gpt-4.1-mini', 'o3', 'o4-mini'],
  EDGEONE: ['@tx/deepseek-v3', '@tx/deepseek-r1', '@tx/hunyuan-turbos', 'gpt-5-mini', 'gpt-4.1-mini'],
  CUSTOM: ['local-llama-3.1-70b', 'qwen2.5-72b-instruct', 'mistral-large'],
};

type SettingsApi = Pick<
  YuzuApi,
  | 'getLlmSettings'
  | 'updateLlmSettings'
  | 'putLlmKey'
  | 'testLlm'
  | 'listModels'
  | 'getUsage'
  | 'getTrace'
  | 'listTraceLlmCalls'
  | 'getLlmCallPayload'
  | 'listEmails'
  | 'listTrades'
  | 'listPortfolios'
>;

/** v0.0.4 🍊 Mock console (LLM settings), usage, trace and simulation endpoints. */
export function createSettingsApi(ctx: MockContext): SettingsApi {
  const { server } = ctx.world;
  const { state } = server;

  const requireKey = (method: 'GET' | 'POST', path: string) => {
    if (!state.settings.hasKey) {
      throw ctx.fail(method, path, 'NOT_CONFIGURED', 'Add an API key in the console first.', { setting: 'apiKey' });
    }
  };

  return {
    async getLlmSettings() {
      await ctx.latency();
      return clone(state.settings);
    },

    async updateLlmSettings(body) {
      const path = '/api/settings/llm';
      await ctx.latency();
      const fields: Record<string, string> = {};
      if (!PROVIDERS.includes(body.provider)) fields.provider = 'unknown provider';
      if (!/^https?:\/\/\S+$/.test(body.baseUrl.trim())) fields.baseUrl = 'must be an http(s) URL';
      for (const tier of TIERS) {
        const t = body.tiers[tier];
        if (!t?.model.trim()) fields[`tiers.${tier}.model`] = 'must not be empty';
        if (!t || !(t.maxOutputTokens > 0)) fields[`tiers.${tier}.maxOutputTokens`] = 'must be positive';
      }
      if (Object.keys(fields).length > 0) throw ctx.fail('PUT', path, 'BAD_REQUEST', 'Some fields are invalid.', { fields });
      state.settings = { ...state.settings, provider: body.provider, baseUrl: body.baseUrl.trim(), tiers: clone(body.tiers) };
      server.publish('settings.changed', state.settings);
      return clone(state.settings);
    },

    async putLlmKey(apiKey) {
      await ctx.latency();
      const key = apiKey.trim();
      if (key.length < 8) throw ctx.fail('PUT', '/api/settings/llm/key', 'BAD_REQUEST', 'The API key looks too short.');
      state.settings = { ...state.settings, hasKey: true, apiKeyMasked: `${key.slice(0, 3)}…${key.slice(-4)}` };
      server.publish('settings.changed', state.settings);
      return clone(state.settings);
    },

    async testLlm() {
      await ctx.latency();
      requireKey('POST', '/api/settings/llm/test');
      await sleep(randInt(700, 1500));
      const result = { tiers: {} } as LlmTestResult;
      for (const tier of TIERS) {
        const model = state.settings.tiers[tier].model;
        const broken = /bad|unknown|missing/i.test(model);
        result.tiers[tier] = broken
          ? { ok: false, model, strategy: null, latencyMs: null, error: `The model "${model}" does not exist or you do not have access to it.` }
          : { ok: true, model, strategy: tier === 'LIGHT' ? 'json_object' : 'json_schema', latencyMs: randInt(280, 2400), error: null };
      }
      return result;
    },

    async listModels() {
      await ctx.latency();
      requireKey('GET', '/api/settings/llm/models');
      return [...(MODELS[state.settings.provider] ?? [])];
    },

    async getUsage() {
      await ctx.latency();
      return state.meter.snapshot();
    },

    async getTrace(traceId) {
      await ctx.latency();
      const events = state.events.filter((e) => e.traceId === traceId);
      if (events.length === 0) throw ctx.fail('GET', `/api/traces/${traceId}`, 'NOT_FOUND', 'No events recorded for this trace.');
      return clone(events);
    },

    async listTraceLlmCalls(traceId) {
      await ctx.latency();
      return clone(state.llmCalls.filter((c) => c.call.traceId === traceId).map((c) => c.call));
    },

    async getLlmCallPayload(callId) {
      await ctx.latency();
      const recorded = state.llmCalls.find((c) => c.call.id === callId);
      if (!recorded) {
        throw ctx.fail('GET', `/api/llm-calls/${callId}/payload`, 'NOT_FOUND', 'The raw payload of this call is no longer on disk.', { callId });
      }
      return clone(recorded.payload);
    },

    async listEmails() {
      await ctx.latency();
      return clone(state.emails);
    },

    async listTrades() {
      await ctx.latency();
      return clone(state.trades);
    },

    async listPortfolios() {
      await ctx.latency();
      return clone([...state.portfolios.values()]);
    },
  };
}
