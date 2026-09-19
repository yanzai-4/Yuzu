import { EmptyState } from '../../../components/EmptyState';
import { useUsageData } from './useUsageData';
import { UsageView } from './UsageView';

/** v0.0.4 🍊 Usage tab (container): feeds the latest usage snapshot into UsageView. */
export function UsageTab() {
  const { usage, person } = useUsageData();
  if (!usage) {
    return (
      <div className="p-3">
        <EmptyState icon="chart" title="No usage yet">
          Token usage appears after the first model call.
        </EmptyState>
      </div>
    );
  }
  return <UsageView usage={usage} person={person} />;
}
