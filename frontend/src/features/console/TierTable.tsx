import clsx from 'clsx';
import type { Tier } from '../../api/types';
import { inputClass } from '../../components/Field';
import { useSettingsStore } from '../../stores/settings';
import { EFFORTS, TIER_HINTS, TIERS, type ConsoleDraft } from './consoleModel';

const DATALIST_ID = 'yuzu-model-options';

/** v0.0.4 🍊 Model, reasoning effort and max output tokens for the IMPORTANT / DEFAULT / LIGHT tiers. */
export function TierTable({
  draft,
  errors,
  onChange,
}: {
  draft: ConsoleDraft;
  errors: Record<string, string>;
  onChange: (tier: Tier, patch: Partial<ConsoleDraft['tiers'][Tier]>) => void;
}) {
  const models = useSettingsStore((s) => s.models);
  return (
    <div className="space-y-2">
      <datalist id={DATALIST_ID}>
        {models.map((m) => (
          <option key={m} value={m} />
        ))}
      </datalist>
      {TIERS.map((tier) => {
        const row = draft.tiers[tier];
        return (
          <fieldset key={tier} className="grid grid-cols-1 gap-2 rounded-xl border border-line p-2.5 sm:grid-cols-[1fr_130px_130px]">
            <legend className="px-1 text-[11px] font-bold tracking-wider text-ink-2 uppercase">
              {tier} <span className="font-normal tracking-normal text-ink-3 normal-case">· {TIER_HINTS[tier]}</span>
            </legend>
            <label className="flex flex-col gap-1 text-[11px] font-semibold text-ink-3">
              Model
              <input
                list={DATALIST_ID}
                value={row.model}
                onChange={(e) => onChange(tier, { model: e.target.value })}
                placeholder="e.g. gpt-5-mini"
                aria-invalid={errors[`${tier}.model`] ? true : undefined}
                className={clsx(inputClass, 'font-mono', errors[`${tier}.model`] && 'border-danger')}
              />
            </label>
            <label className="flex flex-col gap-1 text-[11px] font-semibold text-ink-3">
              Reasoning effort
              <select value={row.reasoningEffort} onChange={(e) => onChange(tier, { reasoningEffort: e.target.value })} className={inputClass}>
                {EFFORTS.map((effort) => (
                  <option key={effort} value={effort}>
                    {effort || '(default)'}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-1 text-[11px] font-semibold text-ink-3">
              Max output tokens
              <input
                inputMode="numeric"
                value={row.maxOutputTokens}
                onChange={(e) => onChange(tier, { maxOutputTokens: e.target.value.replace(/[^\d]/g, '') })}
                aria-invalid={errors[`${tier}.maxOutputTokens`] ? true : undefined}
                className={clsx(inputClass, 'font-mono', errors[`${tier}.maxOutputTokens`] && 'border-danger')}
              />
            </label>
            {errors[`${tier}.model`] || errors[`${tier}.maxOutputTokens`] ? (
              <p className="text-[11px] text-danger-ink sm:col-span-3">
                {[errors[`${tier}.model`] && `Model: ${errors[`${tier}.model`]}`, errors[`${tier}.maxOutputTokens`] && `Max tokens: ${errors[`${tier}.maxOutputTokens`]}`]
                  .filter(Boolean)
                  .join(' · ')}
              </p>
            ) : null}
          </fieldset>
        );
      })}
    </div>
  );
}
