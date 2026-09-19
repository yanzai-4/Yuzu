import { useState } from 'react';
import { Button } from '../../../components/Button';
import { EmptyState } from '../../../components/EmptyState';
import { Spinner } from '../../../components/Spinner';
import { formatInt } from '../../../lib/format';
import { usePeople } from '../../../stores/selectors';
import { openTrace } from '../../../stores/ui';
import { EventRow } from './EventRow';
import { useAgentFilterOptions, useAgentHistory } from './useTraceData';

const selectClass =
  'h-7 min-w-0 flex-1 rounded-lg border border-line-strong bg-surface px-1.5 text-[11px] font-semibold text-ink-2 outline-none focus:border-accent';

/**
 * v0.0.30 🍊 One coworker's stored history (`GET /api/agents/{id}/events`), newest first, paged backwards
 * with the server's `X-Next-Cursor` header — so the tab is not limited to what the live stream still holds.
 */
export function AgentHistory() {
  const options = useAgentFilterOptions();
  const [agentId, setAgentId] = useState('');
  const chosen = options.some((o) => o.id === agentId) ? agentId : (options[0]?.id ?? '');
  const history = useAgentHistory(chosen);
  const person = usePeople();

  if (options.length === 0) {
    return (
      <div className="p-3">
        <EmptyState icon="activity" title="No coworkers yet">
          Hire someone (or seed the demo team) and their history shows up here.
        </EmptyState>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      <div className="space-y-1.5 border-b border-line px-3 py-2">
        <div className="flex gap-1.5">
          <select aria-label="History of" value={chosen} onChange={(e) => setAgentId(e.target.value)} className={selectClass}>
            {options.map((o) => (
              <option key={o.id} value={o.id}>
                {o.label}
              </option>
            ))}
          </select>
          <Button size="xs" variant="ghost" icon="refresh" loading={history.loading} onClick={history.reload}>
            Reload
          </Button>
        </div>
        <p className="text-[11px] text-ink-3">
          {formatInt(history.events.length)} stored events{history.nextCursor ? ' (more on the server)' : ' (all of them)'}
        </p>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        {history.failed && history.events.length === 0 ? (
          <p className="m-3 rounded-lg bg-danger-soft px-3 py-2 text-xs text-danger-ink">Could not load this history.</p>
        ) : null}
        {history.events.map((event) => (
          <EventRow key={event.id} event={event} agent={person(event.agentId)} onOpenTrace={openTrace} />
        ))}
        {history.loading ? (
          <div className="flex justify-center p-3">
            <Spinner size={16} label="Loading history" />
          </div>
        ) : null}
        {!history.loading && history.events.length === 0 ? (
          <div className="p-3">
            <EmptyState icon="activity" title="Nothing recorded yet">
              This coworker has not reported anything so far.
            </EmptyState>
          </div>
        ) : null}
        {history.nextCursor ? (
          <div className="flex justify-center p-3">
            <Button size="sm" icon="arrowDown" loading={history.loading} onClick={history.loadOlder}>
              Load older events
            </Button>
          </div>
        ) : null}
      </div>
    </div>
  );
}
