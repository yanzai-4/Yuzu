import { create } from 'zustand';
import type { UsageSnapshot } from '../api/types';

/** v0.0.4 🍊 Shape of the usage store. */
export interface UsageState {
  usage: UsageSnapshot | null;
}

/** v0.0.4 🍊 Latest token-usage snapshot (bootstrap + `usage.tick`). */
export const useUsageStore = create<UsageState>()(() => ({ usage: null }));

/** v0.0.4 🍊 Replaces the usage snapshot. */
export function setUsage(usage: UsageSnapshot): void {
  useUsageStore.setState({ usage });
}
