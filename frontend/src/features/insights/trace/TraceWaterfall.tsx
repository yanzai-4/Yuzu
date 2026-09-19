import clsx from 'clsx';
import { useMemo, useState } from 'react';
import { getTrace } from '../../../api/client';
import type { ModuleEvent } from '../../../api/types';
import { AgentAvatar } from '../../../components/Avatars';
import { ModuleChip, PhaseChip } from '../../../components/Badge';
import { Button } from '../../../components/Button';
import { Icon } from '../../../components/Icon';
import { Modal } from '../../../components/Modal';
import { Spinner } from '../../../components/Spinner';
import { PHASE_HUES } from '../../../lib/colors';
import { clockTimeWithSeconds } from '../../../lib/time';
import { useAsync } from '../../../lib/useAsync';
import { useTraceStore } from '../../../stores/trace';
import { closeTrace, useUiStore } from '../../../stores/ui';
import { EventInspector } from './EventInspector';
import { buildSpanTree, mergeTraceEvents, type SpanNode } from './spanTree';
import { llmCallsOf, useTraceLlmCalls } from './useTraceData';

function duration(ms: number): string {
  if (ms < 1000) return '<1 s';
  const s = Math.round(ms / 1000);
  return s < 60 ? `${s} s` : `${Math.floor(s / 60)} m ${s % 60} s`;
}

/** v0.0.30 🍊 One span row: indentation by depth, module, title, final phase, timeline bar and its events. */
function SpanRow({
  span,
  t0,
  total,
  open,
  onToggle,
  selectedId,
  onSelect,
}: {
  span: SpanNode;
  t0: number;
  total: number;
  open: boolean;
  onToggle: () => void;
  selectedId: string | null;
  onSelect: (event: ModuleEvent) => void;
}) {
  const left = total > 0 ? ((span.start - t0) / total) * 100 : 0;
  const width = total > 0 ? Math.max(2, ((span.end - span.start) / total) * 100) : 100;
  const hue = PHASE_HUES[span.finalPhase] ?? '#64748b';
  return (
    <li className="border-b border-line last:border-b-0">
      <button type="button" aria-expanded={open} onClick={onToggle} className="flex w-full items-center gap-2 px-2 py-1.5 text-left hover:bg-surface-2">
        <span className="flex min-w-0 flex-1 items-center gap-1.5" style={{ paddingLeft: span.depth * 14 }}>
          <Icon name="chevronRight" size={12} className={clsx('shrink-0 text-ink-3 transition-transform', open && 'rotate-90')} />
          <ModuleChip module={span.module} />
          <span className={clsx('truncate text-xs', span.finalPhase === 'ERROR' ? 'text-danger-ink' : 'text-ink')}>{span.title}</span>
        </span>
        <PhaseChip phase={span.finalPhase} />
        <span className="w-11 shrink-0 text-right text-[10px] text-ink-3 tabular-nums">{duration(span.end - span.start)}</span>
        <span className="relative hidden h-2.5 w-28 shrink-0 rounded-sm bg-surface-3 sm:block" aria-hidden="true">
          <span className="absolute inset-y-0 rounded-[3px]" style={{ left: `${Math.min(left, 98)}%`, width: `${Math.min(width, 100 - Math.min(left, 98))}%`, background: hue }} />
        </span>
      </button>
      {open ? (
        <ul className="space-y-1 bg-surface-2 px-3 py-2" style={{ paddingLeft: 12 + span.depth * 14 + 18 }}>
          {span.events.map((e) => (
            <li key={e.id}>
              <button
                type="button"
                aria-pressed={selectedId === e.id}
                onClick={() => onSelect(e)}
                className={clsx(
                  'flex w-full items-center gap-1.5 rounded px-1 py-0.5 text-left text-[11px] hover:bg-surface-3',
                  selectedId === e.id && 'bg-surface-3 ring-1 ring-accent',
                )}
                title="Inspect this event and its raw model call"
              >
                <PhaseChip phase={e.phase} />
                <time className="font-mono text-[10px] text-ink-3">{clockTimeWithSeconds(e.time)}</time>
                <span className="min-w-0 flex-1 truncate text-ink-2">{e.text}</span>
                <Icon name="eye" size={11} className="shrink-0 text-ink-3" />
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </li>
  );
}

function WaterfallBody({ traceId }: { traceId: string }) {
  const fetched = useAsync(() => getTrace(traceId), traceId);
  const liveAll = useTraceStore((s) => s.events);
  const [open, setOpen] = useState<Set<string>>(() => new Set());
  const [selected, setSelected] = useState<ModuleEvent | null>(null);
  const { calls, loading: loadingCalls } = useTraceLlmCalls(traceId);
  const tree = useMemo(() => {
    const live = liveAll.filter((e) => e.traceId === traceId).reverse();
    return buildSpanTree(mergeTraceEvents(fetched.data ?? [], live));
  }, [fetched.data, liveAll, traceId]);
  const total = tree.t1 - tree.t0;
  const agents = [...new Set(tree.rows.map((r) => r.agentId))];

  const toggle = (spanId: string) =>
    setOpen((prev) => {
      const next = new Set(prev);
      if (next.has(spanId)) next.delete(spanId);
      else next.add(spanId);
      return next;
    });

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2 text-xs text-ink-2">
        {agents.map((id) => (
          <AgentAvatar key={id} agentId={id} size={20} />
        ))}
        <span>
          {tree.eventCount} events · {tree.rows.length} spans · {duration(total)}
        </span>
        <span className="flex-1" />
        {fetched.loading ? <Spinner size={14} label="Loading trace" /> : null}
        <Button size="xs" variant="ghost" icon="refresh" onClick={fetched.reload}>
          Reload
        </Button>
        <Button size="xs" variant="ghost" onClick={() => setOpen(new Set(tree.rows.map((r) => r.spanId)))}>
          Expand all
        </Button>
      </div>
      {fetched.failed && tree.rows.length === 0 ? (
        <p className="rounded-lg bg-danger-soft px-3 py-2 text-xs text-danger-ink">Could not load this trace.</p>
      ) : null}
      {tree.rows.length > 0 ? (
        <ul className="overflow-hidden rounded-xl border border-line">
          {tree.rows.map((span) => (
            <SpanRow
              key={span.spanId}
              span={span}
              t0={tree.t0}
              total={total}
              open={open.has(span.spanId)}
              onToggle={() => toggle(span.spanId)}
              selectedId={selected?.id ?? null}
              onSelect={(event) => setSelected((current) => (current?.id === event.id ? null : event))}
            />
          ))}
        </ul>
      ) : fetched.loading ? null : (
        <p className="text-xs text-ink-3">No events in this trace.</p>
      )}
      {selected ? (
        <EventInspector event={selected} calls={llmCallsOf(selected, calls)} loadingCalls={loadingCalls} />
      ) : tree.rows.length > 0 ? (
        <p className="text-[11px] text-ink-3">Open a span and pick an event to see its detail and the raw model call.</p>
      ) : null}
    </div>
  );
}

/** v0.0.4 🍊 Modal waterfall of one trace (GET /api/traces/{traceId} merged with live events). */
export function TraceWaterfall() {
  const traceId = useUiStore((s) => s.traceId);
  return (
    <Modal
      open={traceId !== null}
      onClose={closeTrace}
      icon="activity"
      title="Trace waterfall"
      subtitle={traceId ? <span className="font-mono">{traceId}</span> : undefined}
      width="max-w-3xl"
    >
      {traceId ? <WaterfallBody key={traceId} traceId={traceId} /> : null}
    </Modal>
  );
}
