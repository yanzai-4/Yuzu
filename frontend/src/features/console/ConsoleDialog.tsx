import clsx from 'clsx';
import { useState } from 'react';
import type { LlmTestResult, Tier } from '../../api/types';
import { Button } from '../../components/Button';
import { Field, SectionTitle, inputClass } from '../../components/Field';
import { Modal } from '../../components/Modal';
import { useSettingsStore } from '../../stores/settings';
import { closeDialog, useUiStore } from '../../stores/ui';
import { ApiKeySection } from './ApiKeySection';
import { fetchModels, runLlmTest, saveLlmSettings } from './consoleActions';
import { fromDraft, PROVIDER_URLS, toDraft, type ConsoleDraft, type Provider } from './consoleModel';
import { TestResults } from './TestResults';
import { TierTable } from './TierTable';

const PROVIDERS: { id: Provider; label: string }[] = [
  { id: 'OPENAI', label: 'OpenAI' },
  { id: 'EDGEONE', label: 'EdgeOne AI gateway' },
  { id: 'CUSTOM', label: 'Custom (OpenAI-compatible)' },
];

function ConsoleForm() {
  const settings = useSettingsStore((s) => s.settings);
  const modelCount = useSettingsStore((s) => s.models.length);
  const [draft, setDraft] = useState<ConsoleDraft>(() => toDraft(settings));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState<'save' | 'test' | 'models' | null>(null);
  const [result, setResult] = useState<LlmTestResult | null>(null);
  const dirty = JSON.stringify(draft) !== JSON.stringify(toDraft(settings));

  const setTier = (tier: Tier, patch: Partial<ConsoleDraft['tiers'][Tier]>) =>
    setDraft((d) => ({ ...d, tiers: { ...d.tiers, [tier]: { ...d.tiers[tier], ...patch } } }));

  const setProvider = (provider: Provider) =>
    setDraft((d) => ({ ...d, provider, baseUrl: provider === 'CUSTOM' ? d.baseUrl : PROVIDER_URLS[provider] }));

  const save = async () => {
    const { body, errors: found } = fromDraft(draft);
    setErrors(found);
    if (!body) return;
    setBusy('save');
    await saveLlmSettings(body);
    setBusy(null);
  };

  const test = async () => {
    setBusy('test');
    setResult(await runLlmTest());
    setBusy(null);
  };

  const loadModels = async () => {
    setBusy('models');
    await fetchModels();
    setBusy(null);
  };

  return (
    <div className="space-y-5">
      <section className="space-y-3">
        <SectionTitle>Provider</SectionTitle>
        <div className="grid gap-3 sm:grid-cols-[220px_1fr]">
          <Field label="Preset" htmlFor="console-provider">
            <select id="console-provider" value={draft.provider} onChange={(e) => setProvider(e.target.value as Provider)} className={inputClass}>
              {PROVIDERS.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Base URL" htmlFor="console-base-url" error={errors.baseUrl} hint={draft.provider === 'CUSTOM' ? 'Any OpenAI-compatible /v1 endpoint' : 'Set by the preset'}>
            <input
              id="console-base-url"
              value={draft.baseUrl}
              readOnly={draft.provider !== 'CUSTOM'}
              onChange={(e) => setDraft({ ...draft, baseUrl: e.target.value })}
              placeholder="https://example.com/v1"
              className={clsx(inputClass, 'font-mono', draft.provider !== 'CUSTOM' && 'bg-surface-2 text-ink-2')}
            />
          </Field>
        </div>
        <ApiKeySection />
      </section>
      <section className="space-y-3">
        <SectionTitle
          action={
            <Button size="xs" icon="refresh" loading={busy === 'models'} onClick={loadModels} title="GET /api/settings/llm/models">
              Fetch models{modelCount ? ` (${modelCount})` : ''}
            </Button>
          }
        >
          Tiers
        </SectionTitle>
        <TierTable draft={draft} errors={errors} onChange={setTier} />
      </section>
      {result ? (
        <section className="space-y-2">
          <SectionTitle>Connection test</SectionTitle>
          <TestResults result={result} />
        </section>
      ) : null}
      <div className="flex flex-wrap items-center gap-2 border-t border-line pt-4">
        <Button icon="zap" loading={busy === 'test'} onClick={test} title={dirty ? 'Tests the saved settings — save first to test your changes' : 'Ping every tier'}>
          Test
        </Button>
        {dirty ? <span className="text-xs text-ink-3">Unsaved changes (Test uses the saved settings)</span> : null}
        <span className="flex-1" />
        {dirty ? (
          <Button
            variant="ghost"
            onClick={() => {
              setDraft(toDraft(settings));
              setErrors({});
            }}
          >
            Discard
          </Button>
        ) : null}
        <Button variant="primary" icon="check" loading={busy === 'save'} disabled={!dirty} onClick={save}>
          Save settings
        </Button>
      </div>
    </div>
  );
}

/** v0.0.4 🍊 Console dialog: provider preset, base URL, API key, per-tier models, Save and Test. */
export function ConsoleDialog() {
  const open = useUiStore((s) => s.dialog?.kind === 'console');
  return (
    <Modal
      open={open}
      onClose={closeDialog}
      icon="sliders"
      title="Console — model settings"
      subtitle="OpenAI-compatible Chat Completions; tiers map modules to models"
      width="max-w-3xl"
    >
      {open ? <ConsoleForm /> : null}
    </Modal>
  );
}
