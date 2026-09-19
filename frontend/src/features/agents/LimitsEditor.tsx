import { useId } from 'react';
import { Field, inputClass } from '../../components/Field';
import type { LimitsDraft } from './permissions';

const NUMBER_FIELDS: { key: Exclude<keyof LimitsDraft, 'emailAllowedDomains'>; label: string; hint: string; suffix: string }[] = [
  { key: 'emailMaxPerHour', label: 'Emails per hour', hint: 'Hard cap on sent emails', suffix: '/h' },
  { key: 'tradeMaxNotionalUsd', label: 'Max trade notional', hint: 'Larger trades are blocked', suffix: 'USD' },
  { key: 'tradeAutoApproveUsd', label: 'Auto-approve up to', hint: 'Above this a human must approve', suffix: 'USD' },
  { key: 'fileQuotaMb', label: 'File quota', hint: 'Workspace storage', suffix: 'MB' },
];

/** v0.0.4 🍊 Editable numeric and list limits (email domains, trade limits, file quota). */
export function LimitsEditor({
  value,
  onChange,
  errors = {},
  disabled = false,
}: {
  value: LimitsDraft;
  onChange: (next: LimitsDraft) => void;
  errors?: Partial<Record<keyof LimitsDraft, string>>;
  disabled?: boolean;
}) {
  const id = useId();
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <Field
        label="Allowed email domains"
        htmlFor={`${id}-domains`}
        hint="Comma separated, e.g. acme-corp.com, yuzu.dev"
        error={errors.emailAllowedDomains}
        className="sm:col-span-2"
      >
        <input
          id={`${id}-domains`}
          value={value.emailAllowedDomains}
          disabled={disabled}
          onChange={(e) => onChange({ ...value, emailAllowedDomains: e.target.value })}
          placeholder="No domains: the agent cannot email anyone"
          className={inputClass}
        />
      </Field>
      {NUMBER_FIELDS.map((f) => (
        <Field key={f.key} label={f.label} htmlFor={`${id}-${f.key}`} hint={f.hint} error={errors[f.key]}>
          <div className="relative">
            <input
              id={`${id}-${f.key}`}
              inputMode="decimal"
              value={value[f.key]}
              disabled={disabled}
              onChange={(e) => onChange({ ...value, [f.key]: e.target.value })}
              className={`${inputClass} pr-12 font-mono`}
            />
            <span className="pointer-events-none absolute inset-y-0 right-2.5 flex items-center text-[11px] font-semibold text-ink-3">
              {f.suffix}
            </span>
          </div>
        </Field>
      ))}
    </div>
  );
}
