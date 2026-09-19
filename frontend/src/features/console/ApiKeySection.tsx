import { useState, type FormEvent } from 'react';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { inputClass } from '../../components/Field';
import { Icon } from '../../components/Icon';
import { useSettingsStore } from '../../stores/settings';
import { saveApiKey } from './consoleActions';

/** v0.0.4 🍊 API key entry: password field + Save; afterwards only the backend's mask is shown. */
export function ApiKeySection() {
  const settings = useSettingsStore((s) => s.settings);
  const [key, setKey] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!key.trim()) return;
    setSaving(true);
    const ok = await saveApiKey(key.trim());
    setSaving(false);
    if (ok) setKey('');
  };

  return (
    <form onSubmit={submit} className="space-y-1.5">
      <div className="flex items-center gap-2">
        <label htmlFor="console-api-key" className="text-xs font-semibold text-ink-2">
          API key
        </label>
        {settings?.hasKey ? (
          <Badge tone="leaf">
            <Icon name="key" size={10} /> Stored {settings.apiKeyMasked ? `· ${settings.apiKeyMasked}` : ''}
          </Badge>
        ) : (
          <Badge tone="warn">Not set</Badge>
        )}
      </div>
      <div className="flex gap-2">
        <input
          id="console-api-key"
          type="password"
          autoComplete="off"
          spellCheck={false}
          value={key}
          onChange={(e) => setKey(e.target.value)}
          placeholder={settings?.hasKey ? 'Paste a new key to replace the stored one' : 'sk-…'}
          className={`${inputClass} font-mono`}
        />
        <Button type="submit" variant="primary" icon="key" loading={saving} disabled={!key.trim()}>
          Save key
        </Button>
      </div>
      <p className="text-[11px] text-ink-3">The key is encrypted on the server and never sent back to the browser — only a mask.</p>
    </form>
  );
}
