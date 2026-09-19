import { useCallback, useState } from 'react';
import { Virtuoso } from 'react-virtuoso';
import type { ModuleEvent } from '../../../api/types';
import { EmptyState } from '../../../components/EmptyState';
import { usePeople } from '../../../stores/selectors';
import { clearTraceEvents, setTracePaused, TRACE_CAP } from '../../../stores/trace';
import { openTrace } from '../../../stores/ui';
import { EventRow } from './EventRow';
import { TraceFilters } from './TraceFilters';
import { NO_FILTER, useAgentFilterOptions, useFilteredEvents, type TraceFilter } from './useTraceData';

/** v0.0.4 🍊 Live `module.event` log (container): filters + virtualized, newest-first list. */
export function EventStream() {
  const [filter, setFilter] = useState<TraceFilter>(NO_FILTER);
  const { events, total, paused } = useFilteredEvents(filter);
  const agentOptions = useAgentFilterOptions();
  const person = usePeople();
  const itemContent = useCallback(
    (_: number, event: ModuleEvent) => <EventRow event={event} agent={person(event.agentId)} onOpenTrace={openTrace} />,
    [person],
  );
  const filtered = filter.agentId !== 'ALL' || filter.module !== 'ALL' || filter.phase !== 'ALL';

  return (
    <div className="flex h-full flex-col">
      <TraceFilters
        filter={filter}
        onChange={setFilter}
        agentOptions={agentOptions}
        shown={events.length}
        total={total}
        cap={TRACE_CAP}
        paused={paused}
        onTogglePause={() => setTracePaused(!paused)}
        onClear={clearTraceEvents}
      />
      <div className="min-h-0 flex-1">
        {events.length === 0 ? (
          <div className="p-3">
            <EmptyState icon="activity" title={filtered ? 'No matching events' : 'Waiting for events'}>
              {filtered ? 'Try another filter.' : 'Module events stream in as coworkers think and act.'}
            </EmptyState>
          </div>
        ) : (
          <Virtuoso className="h-full" data={events} computeItemKey={(_, e) => e.id} itemContent={itemContent} increaseViewportBy={400} />
        )}
      </div>
    </div>
  );
}
