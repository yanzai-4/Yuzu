import clsx from 'clsx';
import { useState, type FormEvent } from 'react';
import { listRoles } from '../../api/client';
import type { Agent, ApiError, Permission, RoleKey, RolePreset } from '../../api/types';
import { Button } from '../../components/Button';
import { Field, SectionTitle, inputClass } from '../../components/Field';
import { Icon } from '../../components/Icon';
import { Spinner } from '../../components/Spinner';
import { useAsync } from '../../lib/useAsync';
import { DESK_COUNT } from '../../stores/room';
import { useActiveAgents } from '../../stores/selectors';
import { useUiStore } from '../../stores/ui';
import { hireAgent } from './agentActions';
import { LimitsEditor } from './LimitsEditor';
import { PermissionChecklist } from './PermissionChecklist';
import { draftToLimits, limitsToDraft, ROLE_LABELS, type LimitsDraft } from './permissions';

interface HireDraft {
  role: RoleKey;
  title: string;
  scopeText: string;
  persona: string;
  permissions: Permission[];
  limits: LimitsDraft;
}

/** v0.0.4 🍊 Explains the AGENT_LIMIT rule (max 8 coworkers). */
export function OfficeFullNotice({ error }: { error?: ApiError | null }) {
  return (
    <div role="alert" className="flex gap-3 rounded-xl border border-warn-line bg-warn-bg p-3 text-warn-ink">
      <Icon name="building" size={20} className="mt-0.5 shrink-0" />
      <div>
        <p className="text-sm font-bold">The office is full — all {DESK_COUNT} desks are taken</p>
        <p className="text-xs">
          {error?.message ?? 'A workgroup can have at most 8 agents.'} Retire a coworker from the inspector to free a desk.
        </p>
      </div>
    </div>
  );
}

/** v0.0.4 🍊 Hire form: pick a role preset, adjust title/scope/persona, permissions and limits, create. */
export function HireAgentForm({ onHired }: { onHired: (agent: Agent) => void }) {
  const roles = useAsync(listRoles, 'roles');
  const preferredRole = useUiStore((s) => s.hireRole);
  const full = useActiveAgents().length >= DESK_COUNT;
  const [draft, setDraft] = useState<HireDraft | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [limitErrors, setLimitErrors] = useState<Partial<Record<keyof LimitsDraft, string>>>({});

  const pick = (preset: RolePreset) => {
    setDraft({
      role: preset.role,
      title: preset.title,
      scopeText: preset.scopeText,
      persona: '',
      permissions: [...preset.permissions],
      limits: limitsToDraft(preset.limits),
    });
    setError(null);
    setLimitErrors({});
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!draft) return;
    const { limits, errors } = draftToLimits(draft.limits);
    setLimitErrors(errors);
    if (Object.keys(errors).length > 0 || !draft.title.trim()) return;
    setSubmitting(true);
    const result = await hireAgent({
      role: draft.role,
      title: draft.title.trim(),
      scopeText: draft.scopeText.trim(),
      ...(draft.persona.trim() ? { persona: draft.persona.trim() } : {}),
      permissions: draft.permissions,
      limits,
    });
    setSubmitting(false);
    if (result.ok) onHired(result.value);
    else setError(result.error);
  };

  const togglePermission = (permission: Permission, on: boolean) =>
    setDraft((d) => d && { ...d, permissions: on ? [...d.permissions, permission] : d.permissions.filter((p) => p !== permission) });

  return (
    <form onSubmit={submit} className="space-y-4">
      {full || error?.code === 'AGENT_LIMIT' ? <OfficeFullNotice error={error} /> : null}
      <SectionTitle>1 · Pick a role</SectionTitle>
      {roles.loading && !roles.data ? (
        <p className="flex items-center gap-2 text-sm text-ink-3">
          <Spinner size={14} /> Loading role presets…
        </p>
      ) : roles.failed ? (
        <Button size="sm" icon="refresh" onClick={roles.reload}>
          Retry loading roles
        </Button>
      ) : (
        <div className="grid gap-2 sm:grid-cols-2" role="radiogroup" aria-label="Role preset">
          {(roles.data ?? []).map((preset) => {
            const selected = draft?.role === preset.role;
            return (
              <button
                key={preset.role}
                type="button"
                role="radio"
                aria-checked={selected}
                onClick={() => pick(preset)}
                className={clsx(
                  'rounded-xl border p-3 text-left transition-colors',
                  selected ? 'border-accent bg-accent-soft ring-2 ring-accent/25' : 'border-line hover:border-line-strong hover:bg-surface-2',
                  !draft && preferredRole === preset.role && 'border-accent',
                )}
              >
                <span className="flex items-center justify-between gap-2">
                  <span className="text-sm font-bold">{preset.title}</span>
                  <span className="text-[10px] font-semibold text-ink-3 uppercase">{ROLE_LABELS[preset.role] ?? preset.role}</span>
                </span>
                <span className="mt-1 block text-xs text-ink-2">{preset.description}</span>
                <span className="mt-1.5 block text-[11px] text-ink-3">{preset.permissions.length} permissions</span>
              </button>
            );
          })}
        </div>
      )}
      {draft ? (
        <>
          <SectionTitle>2 · Make it yours</SectionTitle>
          <p className="flex items-center gap-1.5 rounded-lg bg-surface-2 px-2.5 py-1.5 text-xs text-ink-2">
            <Icon name="sparkles" size={14} className="text-accent" /> The name is assigned automatically: the next free citrus
            (Lemon, Mandarin, Grapefruit…).
          </p>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Title" htmlFor="hire-title" className="sm:col-span-2" error={draft.title.trim() ? undefined : 'A title is required'}>
              <input id="hire-title" value={draft.title} maxLength={80} onChange={(e) => setDraft({ ...draft, title: e.target.value })} className={inputClass} />
            </Field>
            <Field label="Scope" htmlFor="hire-scope" hint="What this coworker is responsible for">
              <textarea id="hire-scope" rows={3} value={draft.scopeText} onChange={(e) => setDraft({ ...draft, scopeText: e.target.value })} className={inputClass} />
            </Field>
            <Field label="Persona" htmlFor="hire-persona" hint="Optional: tone and working style">
              <textarea
                id="hire-persona"
                rows={3}
                value={draft.persona}
                placeholder="e.g. Friendly, concise, double-checks numbers"
                onChange={(e) => setDraft({ ...draft, persona: e.target.value })}
                className={inputClass}
              />
            </Field>
          </div>
          <SectionTitle>3 · Permissions</SectionTitle>
          <PermissionChecklist value={draft.permissions} onToggle={togglePermission} />
          <SectionTitle>4 · Limits</SectionTitle>
          <LimitsEditor value={draft.limits} errors={limitErrors} onChange={(limits) => setDraft({ ...draft, limits })} />
          {error && error.code !== 'AGENT_LIMIT' ? (
            <p role="alert" className="rounded-lg bg-danger-soft px-3 py-2 text-xs text-danger-ink">
              <strong>{error.code}</strong> — {error.message}
            </p>
          ) : null}
          <div className="flex justify-end">
            <Button type="submit" variant="primary" icon="plus" loading={submitting} disabled={full || !draft.title.trim()}>
              Hire coworker
            </Button>
          </div>
        </>
      ) : null}
    </form>
  );
}
