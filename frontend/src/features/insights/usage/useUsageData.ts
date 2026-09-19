import type { UsageSnapshot } from '../../../api/types';
import type { PersonRef } from '../../../stores/people';
import { usePeople } from '../../../stores/selectors';
import { useUsageStore } from '../../../stores/usage';

/** v0.0.4 🍊 Data of the Usage tab: the latest snapshot and a resolver for agent keys. */
export function useUsageData(): { usage: UsageSnapshot | null; person: (id: string) => PersonRef | null } {
  const usage = useUsageStore((s) => s.usage);
  const person = usePeople();
  return { usage, person };
}
