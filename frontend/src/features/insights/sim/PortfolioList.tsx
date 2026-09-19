import type { Portfolio } from '../../../api/types';
import { PersonAvatar } from '../../../components/Avatars';
import { EmptyState } from '../../../components/EmptyState';
import { formatQty, formatUsd } from '../../../lib/format';
import type { WithAgent } from './useSimData';

/** v0.0.4 🍊 One agent's simulated portfolio: cash and positions at cost (props only). */
function PortfolioCard({ data }: { data: WithAgent<Portfolio> }) {
  const { item: portfolio, agent } = data;
  const invested = portfolio.positions.reduce((sum, p) => sum + p.qty * p.avgPrice, 0);
  return (
    <article className="rounded-xl border border-line bg-surface p-3">
      <header className="flex items-center gap-2">
        <PersonAvatar person={agent} size={26} />
        <h4 className="flex-1 text-sm font-bold">{agent?.name ?? portfolio.agentId}</h4>
      </header>
      <dl className="mt-2 grid grid-cols-2 gap-2">
        <div className="rounded-lg bg-surface-2 px-2.5 py-1.5">
          <dt className="text-[11px] text-ink-3">Cash</dt>
          <dd className="text-base font-semibold">{formatUsd(portfolio.cash)}</dd>
        </div>
        <div className="rounded-lg bg-surface-2 px-2.5 py-1.5">
          <dt className="text-[11px] text-ink-3">Invested (at cost)</dt>
          <dd className="text-base font-semibold">{formatUsd(invested)}</dd>
        </div>
      </dl>
      {portfolio.positions.length > 0 ? (
        <table className="mt-2 w-full text-left text-xs tabular-nums">
          <thead className="text-[11px] text-ink-3">
            <tr>
              <th scope="col" className="py-1 font-semibold">
                Symbol
              </th>
              <th scope="col" className="py-1 text-right font-semibold">
                Qty
              </th>
              <th scope="col" className="py-1 text-right font-semibold">
                Avg price
              </th>
              <th scope="col" className="py-1 text-right font-semibold">
                Cost basis
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {portfolio.positions.map((p) => (
              <tr key={p.symbol}>
                <th scope="row" className="py-1 font-mono font-bold">
                  {p.symbol}
                </th>
                <td className="py-1 text-right">{formatQty(p.qty)}</td>
                <td className="py-1 text-right">{formatUsd(p.avgPrice)}</td>
                <td className="py-1 text-right">{formatUsd(p.qty * p.avgPrice)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p className="mt-2 text-xs text-ink-3">No open positions.</p>
      )}
    </article>
  );
}

/** v0.0.4 🍊 Every simulated portfolio (props only). */
export function PortfolioList({ portfolios }: { portfolios: WithAgent<Portfolio>[] }) {
  if (portfolios.length === 0) {
    return (
      <EmptyState icon="trend" title="No portfolios">
        Agents that can trade get a simulated portfolio.
      </EmptyState>
    );
  }
  return (
    <div className="space-y-2">
      {portfolios.map((data) => (
        <PortfolioCard key={data.item.agentId} data={data} />
      ))}
    </div>
  );
}
