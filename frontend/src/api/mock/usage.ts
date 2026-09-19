import type { ModuleKind, Tier, UsageRow, UsageSnapshot } from '../types';
import { nowText, randInt } from './util';

/** v0.0.4 🍊 One simulated LLM call. */
export interface SimulatedCall {
  agentId: string;
  module: ModuleKind;
  prompt: number;
  /** null when the model does not report cached tokens. */
  cached: number | null;
  completion: number;
  reasoning: number;
  retries: number;
  error: boolean;
}

interface Accum {
  calls: number;
  attempts: number;
  promptTokens: number;
  cachedTokens: number;
  completionTokens: number;
  reasoningTokens: number;
  errors: number;
  retries: number;
  /** Prompt tokens of the calls that reported caching (hit-rate denominator). */
  reportedPrompt: number;
}

/** v0.0.4 🍊 Tier of each module (matches the architecture table). */
export function tierOf(module: ModuleKind): Tier {
  if (module === 'MAIN') return 'IMPORTANT';
  if (module === 'MONITOR' || module === 'WM_COMPACTOR') return 'LIGHT';
  return 'DEFAULT';
}

/** v0.0.4 🍊 Model used by each tier in the mock. */
export const TIER_MODELS: Record<Tier, string> = { IMPORTANT: 'gpt-5', DEFAULT: 'gpt-5-mini', LIGHT: 'gpt-5-nano' };

/** v0.0.4 🍊 Aggregates simulated calls into the UsageSnapshot shape. */
export class UsageMeter {
  private readonly total = empty();
  private readonly dims = {
    agent: new Map<string, Accum>(),
    module: new Map<string, Accum>(),
    tier: new Map<string, Accum>(),
    model: new Map<string, Accum>(),
  };
  private localHits = 0;
  private localLookups = 0;

  /** v0.0.4 🍊 Records one call in every dimension. */
  record(call: SimulatedCall): void {
    const tier = tierOf(call.module);
    const keys = { agent: call.agentId, module: call.module, tier, model: TIER_MODELS[tier] };
    for (const [dim, key] of Object.entries(keys) as [keyof typeof keys, string][]) {
      const map = this.dims[dim];
      let acc = map.get(key);
      if (!acc) map.set(key, (acc = empty()));
      add(acc, call);
    }
    add(this.total, call);
    this.localLookups += randInt(1, 3);
    if (Math.random() < 0.35) this.localHits += 1;
  }

  /** v0.0.4 🍊 Generates a realistic call for an agent/module. */
  simulate(agentId: string, module: ModuleKind, error = false): void {
    const tier = tierOf(module);
    const prompt = randInt(tier === 'IMPORTANT' ? 5000 : 1500, tier === 'IMPORTANT' ? 14000 : 6000);
    const cached = tier === 'LIGHT' ? null : Math.round(prompt * (0.55 + Math.random() * 0.35));
    this.record({
      agentId,
      module,
      prompt,
      cached,
      completion: randInt(60, tier === 'IMPORTANT' ? 900 : 400),
      reasoning: tier === 'IMPORTANT' ? randInt(100, 1200) : tier === 'DEFAULT' ? randInt(0, 200) : 0,
      retries: Math.random() < 0.06 ? 1 : 0,
      error,
    });
  }

  /** v0.0.4 🍊 The current snapshot. */
  snapshot(): UsageSnapshot {
    const rows = (map: Map<string, Accum>) =>
      [...map.entries()].map(([key, acc]) => toRow(key, acc)).sort((a, b) => b.promptTokens - a.promptTokens);
    return {
      totals: toRow('total', this.total),
      byAgent: rows(this.dims.agent),
      byModule: rows(this.dims.module),
      byTier: rows(this.dims.tier),
      byModel: rows(this.dims.model),
      localCacheHits: this.localHits,
      localCacheLookups: this.localLookups,
      time: nowText(),
    };
  }
}

function empty(): Accum {
  return {
    calls: 0,
    attempts: 0,
    promptTokens: 0,
    cachedTokens: 0,
    completionTokens: 0,
    reasoningTokens: 0,
    errors: 0,
    retries: 0,
    reportedPrompt: 0,
  };
}

function add(acc: Accum, call: SimulatedCall): void {
  acc.calls += 1;
  acc.attempts += 1 + call.retries;
  acc.promptTokens += call.prompt;
  acc.completionTokens += call.completion;
  acc.reasoningTokens += call.reasoning;
  acc.retries += call.retries;
  acc.errors += call.error ? 1 : 0;
  if (call.cached !== null) {
    acc.cachedTokens += call.cached;
    acc.reportedPrompt += call.prompt;
  }
}

function toRow(key: string, acc: Accum): UsageRow {
  const { reportedPrompt, ...rest } = acc;
  return { key, ...rest, hitRate: reportedPrompt > 0 ? acc.cachedTokens / reportedPrompt : null };
}
