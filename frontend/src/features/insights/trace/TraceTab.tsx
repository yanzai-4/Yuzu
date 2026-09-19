import { useState } from 'react';
import { Tabs } from '../../../components/Tabs';
import { clearErrors } from '../../../stores/trace';
import { AgentHistory } from './AgentHistory';
import { ErrorList } from './ErrorList';
import { EventStream } from './EventStream';
import { IncidentList } from './IncidentList';
import { TraceControls } from './TraceControls';
import { useErrorViews, useIncidentViews } from './useTraceData';

/**
 * v0.0.30 🍊 Trace tab (container): agent controls, the live module events, one coworker's stored history,
 * security incidents and the error log. Clicking a trace id anywhere opens the waterfall.
 */
export function TraceTab() {
  const [view, setView] = useState<'events' | 'history' | 'incidents' | 'errors'>('events');
  const incidents = useIncidentViews();
  const errors = useErrorViews();
  return (
    <div className="flex h-full flex-col">
      <TraceControls />
      <div className="px-3 pt-3 pb-2">
        <Tabs
          label="Trace views"
          value={view}
          onChange={setView}
          stretch
          tabs={[
            { id: 'events', label: 'Live', icon: 'activity' },
            { id: 'history', label: 'History', icon: 'clock' },
            { id: 'incidents', label: 'Incidents', icon: 'shield', count: incidents.length },
            { id: 'errors', label: 'Errors', icon: 'alert', count: errors.length, alert: errors.length > 0 },
          ]}
        />
      </div>
      <div className="min-h-0 flex-1">
        {view === 'events' ? (
          <EventStream />
        ) : view === 'history' ? (
          <AgentHistory />
        ) : (
          <div className="h-full overflow-y-auto px-3 pb-3">
            {view === 'incidents' ? <IncidentList incidents={incidents} /> : <ErrorList errors={errors} onClear={clearErrors} />}
          </div>
        )}
      </div>
    </div>
  );
}
