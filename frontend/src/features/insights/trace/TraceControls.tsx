import { useMemo, useState } from 'react';
import type { Agent } from '../../../api/types';
import { Button } from '../../../components/Button';
import { Icon } from '../../../components/Icon';
import { useRoomStore } from '../../../stores/room';
import { controlAgent, controlRoom } from '../../agents/agentActions';

type Busy = 'interrupt' | 'pause' | 'resume' | 'stop-all' | 'resume-all' | null;

const selectClass =
  'h-7 min-w-0 flex-1 rounded-lg border border-line-strong bg-surface px-1.5 text-[11px] font-semibold text-ink-2 outline-none focus:border-accent';

/**
 * v0.0.30 🍊 The humans' controls above the trace: interrupt / pause / resume one coworker and stop or
 * resume the whole room. Every click applies optimistically and rolls back (with a toast) on failure.
 */
export function TraceControls() {
  const agents = useRoomStore((s) => s.agents);
  const [selected, setSelected] = useState<string>('');
  const [busy, setBusy] = useState<Busy>(null);

  const present = useMemo(
    () => Object.values(agents).filter((a) => a.state !== 'RETIRED').sort((a, b) => a.name.localeCompare(b.name)),
    [agents],
  );
  const agent: Agent | undefined = present.find((a) => a.agentId === selected) ?? present[0];
  const pausedCount = present.filter((a) => a.state === 'PAUSED').length;
  const allPaused = present.length > 0 && pausedCount === present.length;

  const run = async (key: NonNullable<Busy>, action: () => Promise<unknown>) => {
    setBusy(key);
    await action();
    setBusy(null);
  };

  if (present.length === 0) {
    return null;
  }

  return (
    <div className="space-y-1.5 border-b border-line px-3 py-2">
      <div className="flex items-center gap-1.5">
        <Icon name="stop" size={12} className="shrink-0 text-ink-3" />
        <select
          aria-label="Coworker to control"
          value={agent?.agentId ?? ''}
          onChange={(e) => setSelected(e.target.value)}
          className={selectClass}
        >
          {present.map((a) => (
            <option key={a.agentId} value={a.agentId}>
              {a.name}
              {a.state === 'PAUSED' ? ' (paused)' : ''}
            </option>
          ))}
        </select>
        <Button
          size="xs"
          icon="stop"
          loading={busy === 'interrupt'}
          disabled={!agent}
          onClick={() => agent && run('interrupt', () => controlAgent(agent.agentId, 'interrupt'))}
          title="Stop what this coworker is doing right now (streaming stops within a second)"
        >
          Interrupt
        </Button>
        {agent?.state === 'PAUSED' ? (
          <Button
            size="xs"
            variant="leaf"
            icon="play"
            loading={busy === 'resume'}
            onClick={() => run('resume', () => controlAgent(agent.agentId, 'resume'))}
            title="Let this coworker read messages again"
          >
            Resume
          </Button>
        ) : (
          <Button
            size="xs"
            icon="pause"
            loading={busy === 'pause'}
            disabled={!agent}
            onClick={() => agent && run('pause', () => controlAgent(agent.agentId, 'pause'))}
            title="Stop this coworker from reading new messages"
          >
            Pause
          </Button>
        )}
      </div>
      <div className="flex items-center gap-1.5 text-[11px] text-ink-3">
        <span className="flex-1 truncate">
          {pausedCount === 0
            ? `${present.length} coworker${present.length === 1 ? '' : 's'} at work`
            : `${pausedCount} of ${present.length} paused`}
        </span>
        {allPaused ? (
          <Button
            size="xs"
            variant="leaf"
            icon="play"
            loading={busy === 'resume-all'}
            onClick={() => run('resume-all', () => controlRoom('resume-all'))}
          >
            Resume all
          </Button>
        ) : (
          <Button
            size="xs"
            variant="danger"
            icon="stop"
            loading={busy === 'stop-all'}
            onClick={() => run('stop-all', () => controlRoom('stop-all'))}
            title="Pause every coworker and cancel whatever they are doing"
          >
            Stop all
          </Button>
        )}
      </div>
    </div>
  );
}
