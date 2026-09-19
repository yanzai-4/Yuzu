import { create } from 'zustand';
import type { Email, Portfolio, Trade } from '../api/types';
import { parseNaturalTime } from '../lib/time';
import { CowList, CowRecord } from './cow';

/** v0.0.4 🍊 Shape of the simulation store. */
export interface SimState {
  emails: Record<string, Email>;
  /** Email ids, newest first. */
  emailOrder: string[];
  trades: Record<string, Trade>;
  /** Trade ids, newest first. */
  tradeOrder: string[];
  /** agentId → portfolio. */
  portfolios: Record<string, Portfolio>;
}

/** v0.0.4 🍊 Simulated emails, trades and portfolios (nothing here is real). */
export const useSimStore = create<SimState>()(() => ({
  emails: {},
  emailOrder: [],
  trades: {},
  tradeOrder: [],
  portfolios: {},
}));

/** v0.0.4 🍊 Sorts records newest first by their natural-language time (stable for ties). */
export function newestFirst<T extends { id: string; time: string }>(items: readonly T[]): string[] {
  return items
    .map((item, index) => ({ id: item.id, t: parseNaturalTime(item.time), index }))
    .sort((a, b) => (Number.isNaN(b.t) || Number.isNaN(a.t) ? 0 : b.t - a.t) || b.index - a.index)
    .map((entry) => entry.id);
}

/** v0.0.4 🍊 Batched, copy-on-write writer of the simulation store used by the event reducer. */
export class SimDraft {
  private readonly emails: CowRecord<Email>;
  private readonly emailOrder: CowList<string>;
  private readonly trades: CowRecord<Trade>;
  private readonly tradeOrder: CowList<string>;
  private readonly portfolios: CowRecord<Portfolio>;

  /** v0.0.4 🍊 Starts a draft from the current store state. */
  constructor(state: SimState = useSimStore.getState()) {
    this.emails = new CowRecord(state.emails);
    this.emailOrder = new CowList(state.emailOrder);
    this.trades = new CowRecord(state.trades);
    this.tradeOrder = new CowList(state.tradeOrder);
    this.portfolios = new CowRecord(state.portfolios);
  }

  /** v0.0.4 🍊 Inserts (at the top) or replaces an email. */
  upsertEmail(email: Email): void {
    if (!this.emails.get(email.id)) this.emailOrder.mutable().unshift(email.id);
    this.emails.set(email.id, email);
  }

  /** v0.0.4 🍊 Inserts (at the top) or replaces a trade. */
  upsertTrade(trade: Trade): void {
    if (!this.trades.get(trade.id)) this.tradeOrder.mutable().unshift(trade.id);
    this.trades.set(trade.id, trade);
  }

  /** v0.0.4 🍊 Replaces an agent's portfolio. */
  upsertPortfolio(portfolio: Portfolio): void {
    this.portfolios.set(portfolio.agentId, portfolio);
  }

  /** v0.0.4 🍊 Writes every changed collection to the store in one update. */
  commit(): void {
    const patch: Partial<SimState> = {};
    if (this.emails.changed) patch.emails = this.emails.value;
    if (this.emailOrder.changed) patch.emailOrder = this.emailOrder.value;
    if (this.trades.changed) patch.trades = this.trades.value;
    if (this.tradeOrder.changed) patch.tradeOrder = this.tradeOrder.value;
    if (this.portfolios.changed) patch.portfolios = this.portfolios.value;
    if (Object.keys(patch).length > 0) useSimStore.setState(patch);
  }
}
