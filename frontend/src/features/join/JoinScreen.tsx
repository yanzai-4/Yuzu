import { motion } from 'motion/react';
import { useState, type FormEvent } from 'react';
import { IS_MOCK, joinSession } from '../../api/client';
import { ApiRequestError } from '../../api/http';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { CitrusAvatar } from '../../components/citrus/CitrusAvatar';
import { inputClass } from '../../components/Field';
import { setSessionUser } from '../../stores/session';

const USERNAME = /^[\p{L}\p{N}._-]{2,32}$/u;
const CREW = [
  { key: 'lime', color: '#7cc242', delay: 0.2 },
  { key: 'yuzu', color: '#f5c518', delay: 0 },
  { key: 'kumquat', color: '#ff8c1a', delay: 0.4 },
];

/** v0.0.4 🍊 Join screen: pick a username (no password) to enter the workspace. */
export function JoinScreen() {
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const username = name.trim();
    if (!USERNAME.test(username)) {
      setError('Use 2–32 letters, digits, dots, dashes or underscores (no spaces) so coworkers can @mention you.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      setSessionUser(await joinSession(username));
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.apiError.message : 'Could not join. Please try again.');
      setBusy(false);
    }
  };

  return (
    <div className="office-floor grid min-h-dvh place-items-center p-4">
      <motion.form
        onSubmit={submit}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        className="w-full max-w-md rounded-3xl border border-line bg-surface p-6 shadow-lg sm:p-8"
      >
        <div className="mb-4 flex items-end justify-center gap-1" aria-hidden="true">
          {CREW.map((c) => (
            <motion.div
              key={c.key}
              animate={{ y: [0, -8, 0] }}
              transition={{ duration: 1.8, repeat: Infinity, ease: 'easeInOut', delay: c.delay }}
            >
              <CitrusAvatar avatarKey={c.key} color={c.color} size={c.key === 'yuzu' ? 76 : 56} />
            </motion.div>
          ))}
        </div>
        <h1 className="text-center text-2xl font-extrabold tracking-tight">Welcome to Yuzu 🍊</h1>
        <p className="mt-1 text-center text-sm text-ink-2">
          A workspace where humans and citrus-named AI coworkers get real work done together.
        </p>
        <label htmlFor="join-username" className="mt-6 block text-xs font-semibold text-ink-2">
          Your username
        </label>
        <input
          id="join-username"
          autoFocus
          autoComplete="username"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="e.g. alice"
          maxLength={32}
          aria-invalid={error ? true : undefined}
          aria-describedby="join-help"
          className={`${inputClass} mt-1 h-11 text-base`}
        />
        <p id="join-help" className={`mt-1.5 text-xs ${error ? 'text-danger-ink' : 'text-ink-3'}`}>
          {error ?? 'No password needed: coworkers will @mention you by this name.'}
        </p>
        <Button type="submit" variant="primary" loading={busy} className="mt-5 h-11 w-full text-base">
          Join the office
        </Button>
        {IS_MOCK ? (
          <p className="mt-4 flex items-center justify-center gap-2 text-xs text-ink-3">
            <Badge tone="warn">Mock</Badge> Demo mode: the backend is simulated in your browser.
          </p>
        ) : null}
      </motion.form>
    </div>
  );
}
