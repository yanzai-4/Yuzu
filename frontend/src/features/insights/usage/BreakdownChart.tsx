import { memo, useMemo, useState, type ReactNode } from 'react';
import type { UsageRow } from '../../../api/types';
import { formatCompact, formatInt, formatPercent } from '../../../lib/format';

/** The three stacked segments of a usage bar (validated series colors). */
const SEGMENTS = [
  { key: 'cached', label: 'Cached prompt', color: 'var(--series-1)' },
  { key: 'uncached', label: 'Uncached prompt', color: 'var(--series-2)' },
  { key: 'completion', label: 'Completion', color: 'var(--series-3)' },
] as const;

/** v0.0.4 🍊 Legend of the usage bars (always shown: three series). */
export function SegmentLegend() {
  return (
    <ul className="flex flex-wrap gap-x-3 gap-y-1 text-[11px] text-ink-2" aria-label="Legend">
      {SEGMENTS.map((s) => (
        <li key={s.key} className="inline-flex items-center gap-1.5">
          <span className="size-2.5 rounded-sm" style={{ background: s.color }} aria-hidden="true" />
          {s.label}
        </li>
      ))}
    </ul>
  );
}

function values(row: UsageRow): [number, number, number] {
  const cached = Math.min(row.cachedTokens, row.promptTokens);
  return [cached, Math.max(0, row.promptTokens - cached), row.completionTokens];
}

/** v0.0.4 🍊 One horizontal stacked bar: label line, bar with value at the tip, hover/focus details. */
const BarRow = memo(function BarRow({ row, label, max }: { row: UsageRow; label: ReactNode; max: number }) {
  const [hover, setHover] = useState(false);
  const parts = values(row);
  const total = parts[0] + parts[1] + parts[2];
  const ratio = max > 0 ? total / max : 0;
  const shown = parts.map((v, i) => ({ v, i })).filter((p) => p.v > 0);
  return (
    <li
      tabIndex={0}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      onFocus={() => setHover(true)}
      onBlur={() => setHover(false)}
      className="relative rounded-lg px-1.5 py-1 outline-none hover:bg-surface-2 focus-visible:bg-surface-2"
      aria-label={`${row.key}: ${formatInt(total)} tokens, ${formatInt(row.calls)} calls, hit rate ${formatPercent(row.hitRate)}`}
    >
      <div className="flex items-center gap-2 text-xs">
        <span className="min-w-0 flex-1 truncate font-medium text-ink">{label}</span>
        <span className="shrink-0 text-[11px] text-ink-3">
          {formatInt(row.calls)} calls · {formatPercent(row.hitRate, 0)} cached
        </span>
      </div>
      <div className="mt-1 flex items-center gap-1.5">
        <div className="flex h-3 min-w-[3px] gap-[2px]" style={{ width: `calc((100% - 3.25rem) * ${ratio})` }}>
          {shown.map(({ v, i }, n) => (
            <span
              key={i}
              className={n === shown.length - 1 ? 'rounded-r-[4px]' : undefined}
              style={{ flexGrow: v, flexBasis: 0, minWidth: 1, background: SEGMENTS[i]?.color }}
            />
          ))}
        </div>
        <span className="text-[11px] font-semibold text-ink-2">{formatCompact(total)}</span>
      </div>
      {hover ? (
        <div role="tooltip" className="pointer-events-none absolute top-full right-1 z-10 mt-1 w-56 rounded-lg border border-line bg-surface p-2 text-[11px] shadow-md">
          <p className="mb-1 font-semibold text-ink">{label}</p>
          <dl className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-0.5 text-ink-2 tabular-nums">
            {SEGMENTS.map((s, i) => (
              <div key={s.key} className="contents">
                <dt className="flex items-center gap-1.5">
                  <span className="size-2 rounded-sm" style={{ background: s.color }} aria-hidden="true" />
                  {s.label}
                </dt>
                <dd className="text-right">{formatInt(parts[i])}</dd>
              </div>
            ))}
            <dt>Reasoning</dt>
            <dd className="text-right">{formatInt(row.reasoningTokens)}</dd>
            <dt>Calls / attempts</dt>
            <dd className="text-right">
              {formatInt(row.calls)} / {formatInt(row.attempts)}
            </dd>
            <dt>Retries · errors</dt>
            <dd className="text-right">
              {formatInt(row.retries)} · {formatInt(row.errors)}
            </dd>
            <dt>Hit rate</dt>
            <dd className="text-right">{formatPercent(row.hitRate)}</dd>
          </dl>
        </div>
      ) : null}
    </li>
  );
});

/** v0.0.4 🍊 Token usage per key as stacked bars (cached / uncached prompt / completion) or a table. */
export function BreakdownChart({
  rows,
  labelOf,
  asTable,
}: {
  rows: UsageRow[];
  labelOf: (key: string) => ReactNode;
  asTable: boolean;
}) {
  const sorted = useMemo(
    () => [...rows].sort((a, b) => b.promptTokens + b.completionTokens - (a.promptTokens + a.completionTokens)),
    [rows],
  );
  const max = sorted.reduce((m, r) => Math.max(m, r.promptTokens + r.completionTokens), 0);

  if (sorted.length === 0) return <p className="py-4 text-center text-xs text-ink-3">No calls recorded yet.</p>;

  if (asTable) {
    return (
      <div className="overflow-x-auto rounded-lg border border-line">
        <table className="w-full text-left text-[11px] tabular-nums">
          <thead className="bg-surface-2 text-ink-3">
            <tr>
              {['Key', 'Calls', 'Prompt', 'Cached', 'Completion', 'Reasoning', 'Hit rate', 'Errors'].map((h) => (
                <th key={h} scope="col" className="px-2 py-1.5 font-semibold whitespace-nowrap">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {sorted.map((r) => (
              <tr key={r.key}>
                <th scope="row" className="px-2 py-1 font-medium whitespace-nowrap text-ink">
                  {labelOf(r.key)}
                </th>
                <td className="px-2 py-1">{formatInt(r.calls)}</td>
                <td className="px-2 py-1">{formatInt(r.promptTokens)}</td>
                <td className="px-2 py-1">{formatInt(r.cachedTokens)}</td>
                <td className="px-2 py-1">{formatInt(r.completionTokens)}</td>
                <td className="px-2 py-1">{formatInt(r.reasoningTokens)}</td>
                <td className="px-2 py-1">{formatPercent(r.hitRate)}</td>
                <td className="px-2 py-1">{formatInt(r.errors)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  return (
    <ul className="space-y-0.5">
      {sorted.map((r) => (
        <BarRow key={r.key} row={r} label={labelOf(r.key)} max={max} />
      ))}
    </ul>
  );
}
