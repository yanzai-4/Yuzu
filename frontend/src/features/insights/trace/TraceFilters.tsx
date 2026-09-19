import type { EventPhase, ModuleKind } from '../../../api/types';
import { Button } from '../../../components/Button';
import { MODULE_HUES } from '../../../lib/colors';
import { formatInt } from '../../../lib/format';
import { NO_FILTER, type TraceFilter } from './useTraceData';

const MODULES = Object.keys(MODULE_HUES) as ModuleKind[];
const PHASES: EventPhase[] = ['START', 'STATE', 'END', 'ERROR', 'CANCELLED', 'INFO'];
const selectClass =
  'h-7 min-w-0 flex-1 rounded-lg border border-line-strong bg-surface px-1.5 text-[11px] font-semibold text-ink-2 outline-none focus:border-accent';

/** v0.0.4 🍊 Filter row of the live event list (props only): agent / module / phase, pause, clear. */
export function TraceFilters({
  filter,
  onChange,
  agentOptions,
  shown,
  total,
  cap,
  paused,
  onTogglePause,
  onClear,
}: {
  filter: TraceFilter;
  onChange: (next: TraceFilter) => void;
  agentOptions: { id: string; label: string }[];
  shown: number;
  total: number;
  cap: number;
  paused: boolean;
  onTogglePause: () => void;
  onClear: () => void;
}) {
  const filtered = filter.agentId !== 'ALL' || filter.module !== 'ALL' || filter.phase !== 'ALL';
  return (
    <div className="space-y-1.5 border-b border-line px-3 py-2">
      <div className="flex gap-1.5" role="group" aria-label="Filters">
        <select aria-label="Filter by agent" value={filter.agentId} onChange={(e) => onChange({ ...filter, agentId: e.target.value })} className={selectClass}>
          <option value="ALL">All agents</option>
          {agentOptions.map((a) => (
            <option key={a.id} value={a.id}>
              {a.label}
            </option>
          ))}
        </select>
        <select aria-label="Filter by module" value={filter.module} onChange={(e) => onChange({ ...filter, module: e.target.value })} className={selectClass}>
          <option value="ALL">All modules</option>
          {MODULES.map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
        <select aria-label="Filter by phase" value={filter.phase} onChange={(e) => onChange({ ...filter, phase: e.target.value })} className={selectClass}>
          <option value="ALL">All phases</option>
          {PHASES.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
      </div>
      <div className="flex items-center gap-1.5 text-[11px] text-ink-3">
        <span className="flex-1 truncate">
          {paused ? 'Paused · ' : ''}
          {formatInt(shown)} {filtered ? `of ${formatInt(total)} ` : ''}events (keeps the latest {formatInt(cap)})
        </span>
        {filtered ? (
          <Button size="xs" variant="ghost" onClick={() => onChange(NO_FILTER)}>
            Reset
          </Button>
        ) : null}
        <Button size="xs" icon={paused ? 'play' : 'pause'} onClick={onTogglePause}>
          {paused ? 'Resume' : 'Pause'}
        </Button>
        <Button size="xs" variant="ghost" icon="trash" onClick={onClear} title="Clear the live list">
          Clear
        </Button>
      </div>
    </div>
  );
}
