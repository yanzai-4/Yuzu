import clsx from 'clsx';
import { memo } from 'react';
import type { ModuleEvent } from '../../../api/types';
import { PersonAvatar } from '../../../components/Avatars';
import { ModuleChip, PhaseChip } from '../../../components/Badge';
import { PHASE_HUES } from '../../../lib/colors';
import { clockTimeWithSeconds } from '../../../lib/time';
import type { PersonRef } from '../../../stores/people';

/** v0.0.4 🍊 One live trace event (props only), colored by phase; ERROR rows in red. */
export const EventRow = memo(function EventRow({
  event,
  agent,
  onOpenTrace,
}: {
  event: ModuleEvent;
  agent: PersonRef | null;
  onOpenTrace: (traceId: string) => void;
}) {
  const error = event.phase === 'ERROR';
  const traceId = event.traceId;
  return (
    <div
      className={clsx('border-b border-line px-3 py-1.5', error && 'bg-danger-soft/60')}
      style={{ boxShadow: `inset 3px 0 0 ${PHASE_HUES[event.phase] ?? '#64748b'}` }}
    >
      <div className="flex items-center gap-1.5">
        <time className="w-[68px] shrink-0 font-mono text-[10px] text-ink-3" title={event.time}>
          {clockTimeWithSeconds(event.time)}
        </time>
        <PhaseChip phase={event.phase} />
        <ModuleChip module={event.module} />
        <span className="flex min-w-0 items-center gap-1 text-[11px] font-semibold text-ink-2">
          <PersonAvatar person={agent} size={14} />
          <span className="truncate">{agent?.name ?? event.agentId}</span>
        </span>
        <span className="flex-1" />
        {traceId ? (
          <button
            type="button"
            onClick={() => onOpenTrace(traceId)}
            className="shrink-0 truncate font-mono text-[10px] text-info hover:underline"
            title={`Open trace ${traceId}`}
          >
            {traceId.length > 14 ? `…${traceId.slice(-10)}` : traceId}
          </button>
        ) : null}
      </div>
      <p className={clsx('mt-0.5 line-clamp-2 pl-[74px] text-xs break-words', error ? 'text-danger-ink' : 'text-ink')}>{event.text}</p>
    </div>
  );
});
