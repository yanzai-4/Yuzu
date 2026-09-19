import clsx from 'clsx';
import type { ReactNode } from 'react';
import type { UsageSnapshot } from '../../../api/types';
import { Icon } from '../../../components/Icon';
import { formatCompact, formatInt, formatPercent } from '../../../lib/format';

/** v0.0.4 🍊 A stat tile: sentence-case label, compact value, optional sub-line and ratio meter. */
function Tile({
  label,
  value,
  exact,
  sub,
  meter,
  alert = false,
}: {
  label: string;
  value: string;
  exact?: string;
  sub?: ReactNode;
  /** 0..1 fill of the meter (hit rates); omitted for plain counts. */
  meter?: number | null;
  alert?: boolean;
}) {
  return (
    <div className="flex min-w-0 flex-col rounded-xl border border-line bg-surface px-2.5 py-2">
      <span className="truncate text-[11px] font-medium text-ink-3">{label}</span>
      <span className={clsx('mt-0.5 flex items-center gap-1 text-lg leading-tight font-semibold', alert ? 'text-danger-ink' : 'text-ink')} title={exact}>
        {alert ? <Icon name="alert" size={14} aria-label="Needs attention" /> : null}
        {value}
      </span>
      {meter !== undefined ? (
        <span
          className="mt-1.5 block h-1.5 overflow-hidden rounded-full"
          style={{ background: 'color-mix(in oklab, var(--series-1) 22%, var(--surface))' }}
          role="meter"
          aria-label={label}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={meter == null ? undefined : Math.round(meter * 100)}
        >
          <span className="block h-full rounded-full bg-series-1 transition-[width] duration-500" style={{ width: `${(meter ?? 0) * 100}%` }} />
        </span>
      ) : null}
      {sub ? <span className="mt-1 truncate text-[10px] text-ink-3">{sub}</span> : null}
    </div>
  );
}

/** v0.0.4 🍊 KPI row: calls, token counts, cache hit rates, retries and errors. */
export function StatTiles({ usage }: { usage: UsageSnapshot }) {
  const t = usage.totals;
  const localRate = usage.localCacheLookups > 0 ? usage.localCacheHits / usage.localCacheLookups : null;
  return (
    <div className="grid grid-cols-3 gap-2">
      <Tile label="Calls" value={formatCompact(t.calls)} exact={formatInt(t.calls)} sub={`${formatInt(t.attempts)} attempts`} />
      <Tile label="Prompt tokens" value={formatCompact(t.promptTokens)} exact={formatInt(t.promptTokens)} />
      <Tile label="Cached tokens" value={formatCompact(t.cachedTokens)} exact={formatInt(t.cachedTokens)} sub="served from cache" />
      <Tile label="Cache hit rate" value={formatPercent(t.hitRate)} meter={t.hitRate} sub={t.hitRate == null ? 'not reported' : 'cached ÷ prompt'} />
      <Tile label="Completion tokens" value={formatCompact(t.completionTokens)} exact={formatInt(t.completionTokens)} />
      <Tile label="Reasoning tokens" value={formatCompact(t.reasoningTokens)} exact={formatInt(t.reasoningTokens)} />
      <Tile label="Retries" value={formatInt(t.retries)} sub="format retries" />
      <Tile label="Errors" value={formatInt(t.errors)} alert={t.errors > 0} sub={t.errors > 0 ? 'see the Trace tab' : 'all good'} />
      <Tile
        label="Local cache hit rate"
        value={formatPercent(localRate)}
        meter={localRate}
        sub={`${formatInt(usage.localCacheHits)} of ${formatInt(usage.localCacheLookups)} lookups`}
      />
    </div>
  );
}
