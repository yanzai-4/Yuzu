import { useId, useState } from 'react';
import type { Agent, Permission } from '../../api/types';
import { Button } from '../../components/Button';
import { Field, inputClass } from '../../components/Field';
import { patchAgent } from './agentActions';
import { LimitsEditor } from './LimitsEditor';
import { PermissionChecklist } from './PermissionChecklist';
import { draftToLimits, limitsToDraft, type LimitsDraft } from './permissions';

/** v0.0.4 🍊 Editable title, scope and persona of an agent (PATCH on save). */
export function ProfileSection({ agent }: { agent: Agent }) {
  const id = useId();
  const [title, setTitle] = useState(agent.title);
  const [scope, setScope] = useState(agent.scopeText);
  const [persona, setPersona] = useState(agent.persona);
  const [saving, setSaving] = useState(false);
  const dirty = title !== agent.title || scope !== agent.scopeText || persona !== agent.persona;

  const save = async () => {
    setSaving(true);
    await patchAgent(agent.agentId, { title: title.trim(), scopeText: scope.trim(), persona: persona.trim() });
    setSaving(false);
  };
  const reset = () => {
    setTitle(agent.title);
    setScope(agent.scopeText);
    setPersona(agent.persona);
  };

  return (
    <div className="space-y-3">
      <Field label="Title" htmlFor={`${id}-title`} error={title.trim() ? undefined : 'A title is required'}>
        <input id={`${id}-title`} value={title} maxLength={80} onChange={(e) => setTitle(e.target.value)} className={inputClass} />
      </Field>
      <Field label="Scope" htmlFor={`${id}-scope`} hint="What this coworker is responsible for">
        <textarea id={`${id}-scope`} rows={3} value={scope} onChange={(e) => setScope(e.target.value)} className={inputClass} />
      </Field>
      <Field label="Persona" htmlFor={`${id}-persona`} hint="Tone and working style">
        <textarea id={`${id}-persona`} rows={3} value={persona} onChange={(e) => setPersona(e.target.value)} className={inputClass} />
      </Field>
      {dirty ? (
        <div className="flex justify-end gap-2">
          <Button size="sm" variant="ghost" onClick={reset}>
            Discard
          </Button>
          <Button size="sm" variant="primary" icon="check" loading={saving} disabled={!title.trim()} onClick={save}>
            Save profile
          </Button>
        </div>
      ) : null}
    </div>
  );
}

/** v0.0.4 🍊 Permission toggles; each change is saved immediately (PATCH permissions). */
export function PermissionsSection({ agent }: { agent: Agent }) {
  const [pending, setPending] = useState<Permission | null>(null);
  const toggle = async (permission: Permission, enabled: boolean) => {
    setPending(permission);
    const next = enabled ? [...new Set([...agent.permissions, permission])] : agent.permissions.filter((p) => p !== permission);
    await patchAgent(agent.agentId, { permissions: next });
    setPending(null);
  };
  return <PermissionChecklist value={agent.permissions} onToggle={toggle} pending={pending} />;
}

/** v0.0.4 🍊 Editable limits with validation (PATCH limits on save). */
export function LimitsSection({ agent }: { agent: Agent }) {
  const [draft, setDraft] = useState<LimitsDraft>(() => limitsToDraft(agent.limits));
  const [errors, setErrors] = useState<Partial<Record<keyof LimitsDraft, string>>>({});
  const [saving, setSaving] = useState(false);
  const saved = limitsToDraft(agent.limits);
  const dirty = (Object.keys(saved) as (keyof LimitsDraft)[]).some((k) => saved[k] !== draft[k]);

  const save = async () => {
    const { limits, errors: found } = draftToLimits(draft);
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setSaving(true);
    const result = await patchAgent(agent.agentId, { limits });
    setSaving(false);
    if (result.ok) setDraft(limitsToDraft(result.value.limits));
  };

  return (
    <div className="space-y-3">
      <LimitsEditor value={draft} onChange={setDraft} errors={errors} />
      {dirty ? (
        <div className="flex justify-end gap-2">
          <Button
            size="sm"
            variant="ghost"
            onClick={() => {
              setDraft(saved);
              setErrors({});
            }}
          >
            Discard
          </Button>
          <Button size="sm" variant="primary" icon="check" loading={saving} onClick={save}>
            Save limits
          </Button>
        </div>
      ) : null}
    </div>
  );
}
