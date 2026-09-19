import clsx from 'clsx';
import type { LlmTestResult } from '../../api/types';
import { Icon } from '../../components/Icon';
import { formatInt } from '../../lib/format';
import { TIERS } from './consoleModel';

/** v0.0.4 🍊 Per-tier result of the connection test: ok, model, latency, output strategy or error. */
export function TestResults({ result }: { result: LlmTestResult }) {
  return (
    <ul className="divide-y divide-line overflow-hidden rounded-xl border border-line" aria-label="Test results">
      {TIERS.map((tier) => {
        const r = result.tiers?.[tier];
        if (!r) return null;
        return (
          <li key={tier} className={clsx('flex items-start gap-2 px-3 py-2 text-xs', !r.ok && 'bg-danger-soft/50')}>
            <span
              className={clsx('mt-0.5 grid size-5 shrink-0 place-items-center rounded-full', r.ok ? 'bg-leaf text-white dark:text-[#10200a]' : 'bg-danger text-white')}
              aria-label={r.ok ? 'OK' : 'Failed'}
            >
              <Icon name={r.ok ? 'check' : 'x'} size={12} strokeWidth={3} />
            </span>
            <span className="min-w-0 flex-1">
              <span className="flex flex-wrap items-center gap-x-2">
                <strong>{tier}</strong>
                <span className="font-mono text-ink-2">{r.model}</span>
                {r.latencyMs != null ? <span className="text-ink-3">{formatInt(r.latencyMs)} ms</span> : null}
                {r.strategy ? <span className="rounded bg-surface-3 px-1 font-mono text-[10px] text-ink-2">{r.strategy}</span> : null}
              </span>
              {r.error ? <span className="mt-0.5 block text-danger-ink">{r.error}</span> : null}
            </span>
          </li>
        );
      })}
    </ul>
  );
}
