import clsx from 'clsx';
import { useMemo, useState } from 'react';
import { getWorkingMemory, listAgentEvents } from '../../api/client';
import type { ModuleEvent } from '../../api/types';
import { Badge, ModuleChip, PhaseChip } from '../../components/Badge';
import { Button } from '../../components/Button';
import { Spinner } from '../../components/Spinner';
import { clockTimeWithSeconds } from '../../lib/time';
import { useAsync } from '../../lib/useAsync';
import { useTraceStore } from '../../stores/trace';
import { openTrace } from '../../stores/ui';

/** v0.0.4 🍊 Working memory of an agent (digest + entries), fetched on open with a refresh button. */
export function WorkingMemorySection({ agentId }: { agentId: string }) {
  const memory = useAsync(() => getWorkingMemory(agentId), `wm:${agentId}`);
  const entries = useMemo(() => [...(memory.data?.entries ?? [])].reverse(), [memory.data]);
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between text-xs text-ink-3">
        <span>{memory.data ? `${memory.data.entries.length} entries (newest first)` : ' '}</span>
        <Button size="xs" variant="ghost" icon="refresh" loading={memory.loading} onClick={memory.reload}>
          Refresh
        </Button>
      </div>
      {memory.failed ? <p className="text-xs text-danger-ink">Could not load the working memory.</p> : null}
      {memory.loading && !memory.data ? (
        <p className="flex items-center gap-2 text-xs text-ink-3">
          <Spinner size={12} /> Loading…
        </p>
      ) : null}
      {memory.data?.digest ? (
        <div className="rounded-lg border border-line bg-surface-2 px-2.5 py-2 text-xs">
          <p className="mb-0.5 text-[10px] font-bold tracking-wider text-ink-3 uppercase">Digest (compacted)</p>
          <p className="text-ink-2">{memory.data.digest}</p>
        </div>
      ) : null}
      {memory.data && entries.length === 0 ? <p className="text-xs text-ink-3">Nothing in working memory yet.</p> : null}
      <ul className="space-y-1.5">
        {entries.map((entry) => (
          <li key={entry.id} className="rounded-lg border border-line px-2.5 py-1.5">
            <div className="flex items-center gap-1.5">
              <Badge tone={entry.direction === 'IN' ? 'info' : 'accent'}>{entry.direction}</Badge>
              <span className="font-mono text-[10px] font-semibold text-ink-3">{entry.origin}</span>
              <span className="flex-1" />
              <time className="text-[10px] text-ink-3" title={entry.time}>
                {clockTimeWithSeconds(entry.time)}
              </time>
            </div>
            <p className="mt-1 text-xs break-words text-ink-2">{entry.text}</p>
          </li>
        ))}
      </ul>
    </div>
  );
}

function EventLine({ event }: { event: ModuleEvent }) {
  return (
    <li className="flex items-start gap-1.5 py-1 text-xs">
      <PhaseChip phase={event.phase} />
      <ModuleChip module={event.module} />
      <span className={clsx('min-w-0 flex-1 break-words', event.phase === 'ERROR' ? 'text-danger-ink' : 'text-ink-2')}>{event.text}</span>
      {event.traceId ? (
        <button
          type="button"
          onClick={() => openTrace(event.traceId as string)}
          className="shrink-0 font-mono text-[10px] text-info hover:underline"
          title="Open the trace waterfall"
        >
          trace
        </button>
      ) : null}
    </li>
  );
}

/** v0.0.4 🍊 The agent's latest module events (live), with an on-demand history fetch. */
export function RecentActivitySection({ agentId }: { agentId: string }) {
  const events = useTraceStore((s) => s.events);
  const live = useMemo(() => events.filter((e) => e.agentId === agentId).slice(0, 15), [events, agentId]);
  const [history, setHistory] = useState<ModuleEvent[] | null>(null);
  const [loading, setLoading] = useState(false);

  const loadHistory = async () => {
    setLoading(true);
    try {
      setHistory([...(await listAgentEvents(agentId, undefined, 50))].reverse());
    } catch {
      setHistory(null);
    }
    setLoading(false);
  };

  const shown = history ?? live;
  return (
    <div>
      {shown.length === 0 ? <p className="text-xs text-ink-3">No events received yet.</p> : null}
      <ul className="divide-y divide-line">
        {shown.map((event) => (
          <EventLine key={event.id} event={event} />
        ))}
      </ul>
      <Button size="xs" variant="ghost" icon="list" className="mt-1" loading={loading} onClick={history ? () => setHistory(null) : loadHistory}>
        {history ? 'Back to live events' : 'Load history (last 50)'}
      </Button>
    </div>
  );
}
