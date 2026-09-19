import { useState } from 'react';
import { Tabs } from '../../../components/Tabs';
import { clearErrors } from '../../../stores/trace';
import { ErrorList } from './ErrorList';
import { EventStream } from './EventStream';
import { IncidentList } from './IncidentList';
import { useErrorViews, useIncidentViews } from './useTraceData';

/** v0.0.4 🍊 Trace tab (container): live module events, security incidents and the error log. */
export function TraceTab() {
  const [view, setView] = useState<'events' | 'incidents' | 'errors'>('events');
  const incidents = useIncidentViews();
  const errors = useErrorViews();
  return (
    <div className="flex h-full flex-col">
      <div className="px-3 pt-3 pb-2">
        <Tabs
          label="Trace views"
          value={view}
          onChange={setView}
          stretch
          tabs={[
            { id: 'events', label: 'Live events', icon: 'activity' },
            { id: 'incidents', label: 'Incidents', icon: 'shield', count: incidents.length },
            { id: 'errors', label: 'Errors', icon: 'alert', count: errors.length, alert: errors.length > 0 },
          ]}
        />
      </div>
      <div className="min-h-0 flex-1">
        {view === 'events' ? (
          <EventStream />
        ) : (
          <div className="h-full overflow-y-auto px-3 pb-3">
            {view === 'incidents' ? <IncidentList incidents={incidents} /> : <ErrorList errors={errors} onClear={clearErrors} />}
          </div>
        )}
      </div>
    </div>
  );
}
