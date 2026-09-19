import type { LlmSettingsView, Tier, UpdateLlmSettingsRequest } from '../../api/types';

/** v0.0.4 🍊 Provider ids of the console. */
export type Provider = LlmSettingsView['provider'];

/** v0.0.4 🍊 The three model tiers, most capable first. */
export const TIERS: Tier[] = ['IMPORTANT', 'DEFAULT', 'LIGHT'];

/** v0.0.4 🍊 Where each tier is used (from the architecture doc). */
export const TIER_HINTS: Record<Tier, string> = {
  IMPORTANT: 'Main consciousness',
  DEFAULT: 'Chat, safety, planning, tools…',
  LIGHT: 'Monitor summaries',
};

/** v0.0.4 🍊 Base URL of each provider preset (CUSTOM is free text). */
export const PROVIDER_URLS: Record<Exclude<Provider, 'CUSTOM'>, string> = {
  OPENAI: 'https://api.openai.com/v1',
  EDGEONE: 'https://ai-gateway.edgeone.link/v1',
};

/** v0.0.4 🍊 Reasoning-effort choices ('' = provider default). */
export const EFFORTS = ['', 'none', 'minimal', 'low', 'medium', 'high'];

/** v0.0.4 🍊 Editable form state of the console. */
export interface ConsoleDraft {
  provider: Provider;
  baseUrl: string;
  tiers: Record<Tier, { model: string; reasoningEffort: string; maxOutputTokens: string }>;
}

/** v0.0.4 🍊 Settings view → form draft. */
export function toDraft(settings: LlmSettingsView | null): ConsoleDraft {
  const tier = (t: Tier) => {
    const s = settings?.tiers?.[t];
    return { model: s?.model ?? '', reasoningEffort: s?.reasoningEffort ?? '', maxOutputTokens: String(s?.maxOutputTokens ?? 4000) };
  };
  return {
    provider: settings?.provider ?? 'OPENAI',
    baseUrl: settings?.baseUrl ?? PROVIDER_URLS.OPENAI,
    tiers: { IMPORTANT: tier('IMPORTANT'), DEFAULT: tier('DEFAULT'), LIGHT: tier('LIGHT') },
  };
}

/** v0.0.4 🍊 Validates a draft; returns the request body or field errors. */
export function fromDraft(draft: ConsoleDraft): { body: UpdateLlmSettingsRequest | null; errors: Record<string, string> } {
  const errors: Record<string, string> = {};
  if (!/^https?:\/\/\S+$/.test(draft.baseUrl.trim())) errors.baseUrl = 'Enter an http(s) URL';
  const tiers = {} as UpdateLlmSettingsRequest['tiers'];
  for (const t of TIERS) {
    const row = draft.tiers[t];
    const max = Number(row.maxOutputTokens);
    if (!row.model.trim()) errors[`${t}.model`] = 'Required';
    if (!Number.isInteger(max) || max < 1 || max > 1_000_000) errors[`${t}.maxOutputTokens`] = '1 – 1,000,000';
    tiers[t] = { model: row.model.trim(), reasoningEffort: row.reasoningEffort || null, maxOutputTokens: max };
  }
  if (Object.keys(errors).length > 0) return { body: null, errors };
  return { body: { provider: draft.provider, baseUrl: draft.baseUrl.trim(), tiers }, errors };
}
