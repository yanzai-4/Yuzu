import { useCallback, useState } from 'react';
import type { UsageRow, UsageSnapshot } from '../../../api/types';
import { PersonAvatar } from '../../../components/Avatars';
import { Button } from '../../../components/Button';
import { SectionTitle } from '../../../components/Field';
import { Spotlight } from '../../../components/Spotlight';
import { Tabs } from '../../../components/Tabs';
import { clockTimeWithSeconds } from '../../../lib/time';
import type { PersonRef } from '../../../stores/people';
import { setUsageDimension, useUiStore, type UsageDimension } from '../../../stores/ui';
import { BreakdownChart, SegmentLegend } from './BreakdownChart';
import { StatTiles } from './StatTiles';

const ROWS: Record<UsageDimension, (u: UsageSnapshot) => UsageRow[]> = {
  agent: (u) => u.byAgent ?? [],
  module: (u) => u.byModule ?? [],
  tier: (u) => u.byTier ?? [],
  model: (u) => u.byModel ?? [],
};

/** v0.0.4 🍊 Usage tab body (props only): KPI tiles and token breakdowns by agent, module, tier, model. */
export function UsageView({ usage, person }: { usage: UsageSnapshot; person: (id: string) => PersonRef | null }) {
  const dimension = useUiStore((s) => s.usageDimension);
  const [asTable, setAsTable] = useState(false);

  const labelOf = useCallback(
    (key: string) => {
      if (dimension !== 'agent') return <span className="font-mono">{key}</span>;
      const ref = person(key);
      return (
        <span className="inline-flex items-center gap-1.5">
          <PersonAvatar person={ref} size={18} />
          {ref?.name ?? key}
        </span>
      );
    },
    [dimension, person],
  );

  return (
    <div className="space-y-4 p-3">
      <div className="space-y-2">
        <SectionTitle action={<span className="text-[10px] text-ink-3">as of {clockTimeWithSeconds(usage.time)}</span>}>Totals</SectionTitle>
        <Spotlight id="insights.usage.totals">
          <StatTiles usage={usage} />
        </Spotlight>
      </div>
      <div className="space-y-2">
        <SectionTitle
          action={
            <Button size="xs" variant="ghost" icon={asTable ? 'chart' : 'list'} onClick={() => setAsTable((t) => !t)}>
              {asTable ? 'Bars' : 'Table'}
            </Button>
          }
        >
          Tokens by {dimension}
        </SectionTitle>
        <Tabs
          label="Breakdown dimension"
          value={dimension}
          onChange={setUsageDimension}
          stretch
          tabs={[
            { id: 'agent', label: 'Agent' },
            { id: 'module', label: 'Module' },
            { id: 'tier', label: 'Tier' },
            { id: 'model', label: 'Model' },
          ]}
        />
        <Spotlight id="insights.usage.breakdown" className="space-y-2">
          {asTable ? null : <SegmentLegend />}
          <BreakdownChart rows={ROWS[dimension](usage)} labelOf={labelOf} asTable={asTable} />
        </Spotlight>
      </div>
    </div>
  );
}
