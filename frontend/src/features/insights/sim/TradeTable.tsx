import clsx from 'clsx';
import type { Trade } from '../../../api/types';
import { PersonAvatar } from '../../../components/Avatars';
import { Badge, type BadgeTone } from '../../../components/Badge';
import { EmptyState } from '../../../components/EmptyState';
import { formatQty, formatUsd } from '../../../lib/format';
import { clockTime } from '../../../lib/time';
import type { WithAgent } from './useSimData';

const STATUS: Record<Trade['status'], { label: string; tone: BadgeTone }> = {
  PENDING_APPROVAL: { label: 'Pending approval', tone: 'warn' },
  EXECUTED: { label: 'Executed', tone: 'leaf' },
  REJECTED: { label: 'Rejected', tone: 'neutral' },
  BLOCKED: { label: 'Blocked', tone: 'danger' },
};

const HEADERS: [string, string][] = [
  ['Symbol', ''],
  ['Side', ''],
  ['Qty', 'text-right'],
  ['Price', 'text-right'],
  ['Notional', 'text-right'],
  ['Status', ''],
];

/** v0.0.4 🍊 Simulated trades (props only): symbol, side, quantity, price, notional and status. */
export function TradeTable({ trades }: { trades: WithAgent<Trade>[] }) {
  if (trades.length === 0) {
    return (
      <EmptyState icon="trend" title="No trades yet">
        Trades proposed by agents with trading permissions show up here; big ones need your approval in chat.
      </EmptyState>
    );
  }
  return (
    <div className="overflow-x-auto rounded-lg border border-line">
      <table className="w-full min-w-[420px] text-left text-xs">
        <thead className="bg-surface-2 text-[11px] text-ink-3">
          <tr>
            {HEADERS.map(([label, align]) => (
              <th key={label} scope="col" className={clsx('px-2 py-1.5 font-semibold', align)}>
                {label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-line tabular-nums">
          {trades.map(({ item: trade, agent }) => {
            const status = STATUS[trade.status] ?? { label: trade.status, tone: 'neutral' as const };
            return (
              <tr key={trade.id} className={clsx(trade.status === 'PENDING_APPROVAL' && 'bg-warn-bg/30', trade.status === 'BLOCKED' && 'bg-danger-soft/40')}>
                <th scope="row" className="px-2 py-1.5">
                  <span className="flex items-center gap-1.5">
                    <PersonAvatar person={agent} size={18} />
                    <span className="font-mono font-bold">{trade.symbol}</span>
                  </span>
                  <span className="block pl-6 text-[10px] font-normal text-ink-3">{clockTime(trade.time)}</span>
                </th>
                <td className="px-2 py-1.5">
                  <span className={clsx('font-bold', trade.side === 'BUY' ? 'text-ok' : 'text-danger-ink')}>{trade.side}</span>
                </td>
                <td className="px-2 py-1.5 text-right">{formatQty(trade.qty)}</td>
                <td className="px-2 py-1.5 text-right">{formatUsd(trade.price)}</td>
                <td className="px-2 py-1.5 text-right font-semibold">{formatUsd(trade.notional)}</td>
                <td className="px-2 py-1.5">
                  <Badge tone={status.tone} title={trade.reason ?? undefined}>
                    {status.label}
                  </Badge>
                  {trade.reason ? <span className="mt-0.5 block max-w-44 text-[10px] leading-tight text-ink-3">{trade.reason}</span> : null}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
