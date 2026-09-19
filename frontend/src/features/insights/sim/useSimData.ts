import { useMemo } from 'react';
import type { Email, Portfolio, Trade } from '../../../api/types';
import type { PersonRef } from '../../../stores/people';
import { usePeople } from '../../../stores/selectors';
import { useSimStore } from '../../../stores/sim';

/** v0.0.4 🍊 A simulated record with its resolved agent. */
export interface WithAgent<T> {
  item: T;
  agent: PersonRef | null;
}

/** v0.0.4 🍊 Everything the Simulation tab shows (newest first), with agents resolved. */
export interface SimData {
  emails: WithAgent<Email>[];
  trades: WithAgent<Trade>[];
  portfolios: WithAgent<Portfolio>[];
  pendingTrades: number;
}

/** v0.0.4 🍊 Selector hook of the Simulation tab. */
export function useSimData(): SimData {
  const emails = useSimStore((s) => s.emails);
  const emailOrder = useSimStore((s) => s.emailOrder);
  const trades = useSimStore((s) => s.trades);
  const tradeOrder = useSimStore((s) => s.tradeOrder);
  const portfolios = useSimStore((s) => s.portfolios);
  const person = usePeople();
  return useMemo(() => {
    const pick = <T extends { agentId: string }>(item: T | undefined): WithAgent<T>[] =>
      item ? [{ item, agent: person(item.agentId) }] : [];
    const tradeViews = tradeOrder.flatMap((id) => pick(trades[id]));
    return {
      emails: emailOrder.flatMap((id) => pick(emails[id])),
      trades: tradeViews,
      portfolios: Object.values(portfolios).flatMap((p) => pick(p)),
      pendingTrades: tradeViews.filter((t) => t.item.status === 'PENDING_APPROVAL').length,
    };
  }, [emails, emailOrder, trades, tradeOrder, portfolios, person]);
}
