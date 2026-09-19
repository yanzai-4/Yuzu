import { useState } from 'react';
import { Icon } from '../../../components/Icon';
import { Tabs } from '../../../components/Tabs';
import { EmailList } from './EmailList';
import { PortfolioList } from './PortfolioList';
import { TradeTable } from './TradeTable';
import { useSimData } from './useSimData';

/** v0.0.4 🍊 Simulation tab (container): SIMULATED banner, then emails, trades and portfolios. */
export function SimTab() {
  const data = useSimData();
  const [view, setView] = useState<'emails' | 'trades' | 'portfolios'>('emails');
  return (
    <div className="space-y-3 p-3">
      <div role="note" className="sim-stripes overflow-hidden rounded-xl border border-warn-line p-0.5">
        <div className="flex items-center gap-2 rounded-[10px] bg-warn-bg px-3 py-2 text-warn-ink">
          <Icon name="beaker" size={18} className="shrink-0" />
          <p className="text-xs">
            <strong className="mr-1 tracking-widest">SIMULATED</strong>
            No real emails are sent and no real trades are executed.
          </p>
        </div>
      </div>
      <Tabs
        label="Simulation views"
        value={view}
        onChange={setView}
        stretch
        tabs={[
          { id: 'emails', label: 'Emails', icon: 'mail', count: data.emails.length },
          { id: 'trades', label: 'Trades', icon: 'trend', count: data.pendingTrades, alert: data.pendingTrades > 0 },
          { id: 'portfolios', label: 'Portfolios', icon: 'layers' },
        ]}
      />
      {view === 'emails' ? (
        <EmailList emails={data.emails} />
      ) : view === 'trades' ? (
        <TradeTable trades={data.trades} />
      ) : (
        <PortfolioList portfolios={data.portfolios} />
      )}
    </div>
  );
}
