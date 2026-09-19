import { Badge } from '../../../components/Badge';
import { Button } from '../../../components/Button';
import { Collapsible } from '../../../components/Collapsible';
import { EmptyState } from '../../../components/EmptyState';
import { clockTimeWithSeconds } from '../../../lib/time';
import type { PersonRef } from '../../../stores/people';
import type { ErrorEntry } from '../../../stores/trace';

/** v0.0.4 🍊 One failure (props only): code, message, origin (REST or async event), agent, details. */
function ErrorCard({ entry, agent }: { entry: ErrorEntry; agent: PersonRef | null }) {
  const { error } = entry;
  const details = error.details && Object.keys(error.details).length > 0 ? error.details : null;
  return (
    <li className="rounded-lg border border-danger/40 bg-surface p-2.5">
      <div className="flex flex-wrap items-center gap-1.5">
        <code className="rounded bg-danger-soft px-1.5 py-px font-mono text-[10px] font-bold text-danger-ink">{error.code}</code>
        <Badge tone={entry.source === 'request' ? 'info' : entry.source === 'ui' ? 'danger' : 'accent'}>
          {entry.source === 'request' ? 'REST' : entry.source === 'ui' ? 'UI' : 'Async'}
        </Badge>
        {agent ? <span className="text-[11px] font-semibold text-ink-2">{agent.name}</span> : null}
        <span className="flex-1" />
        <time className="text-[10px] text-ink-3" title={error.time}>
          {clockTimeWithSeconds(error.time)}
        </time>
      </div>
      <p className="mt-1 text-xs break-words text-ink">{error.message}</p>
      {entry.context ? <p className="mt-0.5 font-mono text-[10px] break-all text-ink-3">{entry.context}</p> : null}
      {details ? (
        <Collapsible title="Details" className="mt-1">
          <pre className="overflow-x-auto rounded-md bg-surface-3 p-2 font-mono text-[10px] text-ink-2">{JSON.stringify(details, null, 2)}</pre>
        </Collapsible>
      ) : null}
    </li>
  );
}

/** v0.0.4 🍊 Failed requests and asynchronous `error` events (props only), newest first. */
export function ErrorList({ errors, onClear }: { errors: { entry: ErrorEntry; agent: PersonRef | null }[]; onClear: () => void }) {
  if (errors.length === 0) {
    return (
      <EmptyState icon="check" title="No errors">
        Failed requests and asynchronous failures show up here (and as toasts).
      </EmptyState>
    );
  }
  return (
    <div className="space-y-2">
      <div className="flex justify-end">
        <Button size="xs" variant="ghost" icon="trash" onClick={onClear}>
          Clear errors
        </Button>
      </div>
      <ul className="space-y-1.5">
        {errors.map(({ entry, agent }) => (
          <ErrorCard key={entry.key} entry={entry} agent={agent} />
        ))}
      </ul>
    </div>
  );
}
