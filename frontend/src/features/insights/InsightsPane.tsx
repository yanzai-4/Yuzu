import { Panel } from '../../components/Panel';
import { Tabs } from '../../components/Tabs';
import { useTasksStore } from '../../stores/tasks';
import { useTraceStore } from '../../stores/trace';
import { setInsightsTab, useUiStore } from '../../stores/ui';
import { SimTab } from './sim/SimTab';
import { TasksTab } from './tasks/TasksTab';
import { TraceTab } from './trace/TraceTab';
import { UsageTab } from './usage/UsageTab';

/** v0.0.4 🍊 Right pane: Tasks, Usage, Simulation and Trace tabs. */
export function InsightsPane() {
  const tab = useUiStore((s) => s.insightsTab);
  const errors = useTraceStore((s) => s.errors.length);
  const awaiting = useTasksStore((s) => Object.values(s.lists).filter((v) => v.current?.status === 'AWAITING_APPROVAL').length);
  return (
    <Panel label="Insights" icon="chart" title="Insights" subtitle="Tasks, token usage, simulation and trace" bodyClassName="flex flex-col">
      <div className="border-b border-line px-3 py-2">
        <Tabs
          label="Insights"
          value={tab}
          onChange={setInsightsTab}
          stretch
          tabs={[
            { id: 'tasks', label: 'Tasks', icon: 'list', count: awaiting, alert: awaiting > 0 },
            { id: 'usage', label: 'Usage', icon: 'chart' },
            { id: 'sim', label: 'Sim', icon: 'beaker' },
            { id: 'trace', label: 'Trace', icon: 'activity', count: errors, alert: errors > 0 },
          ]}
        />
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        {tab === 'tasks' ? <TasksTab /> : tab === 'usage' ? <UsageTab /> : tab === 'sim' ? <SimTab /> : null}
        {tab === 'trace' ? (
          <div className="h-full">
            <TraceTab />
          </div>
        ) : null}
      </div>
    </Panel>
  );
}
